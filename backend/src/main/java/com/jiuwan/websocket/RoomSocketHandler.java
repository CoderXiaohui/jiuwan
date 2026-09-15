package com.jiuwan.websocket;

import static com.jiuwan.exception.BusinessException.require;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.dto.*;
import com.jiuwan.exception.BusinessException;
import com.jiuwan.service.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper mapper;
  private final RoomService rooms;
  private final RoomViewService views;
  private final GameService games;
  private final IdentityService identity;

  private record Binding(String code, String playerId, String token) {}

  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, Binding> bindings = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    session.setTextMessageSizeLimit(8192);
    session.getAttributes().put("createdAt", System.currentTimeMillis());
    sessions.put(session.getId(), session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String requestId = null;
    try {
      long now = System.currentTimeMillis();
      long window = (long) session.getAttributes().getOrDefault("window", 0L);
      int count = (int) session.getAttributes().getOrDefault("count", 0);
      if (now - window >= 1000) {
        window = now;
        count = 0;
      }
      session.getAttributes().put("window", window);
      session.getAttributes().put("count", count + 1);
      require(count < 20, "RATE_LIMIT", "操作太快啦，稍等一下");
      Requests.Command command = mapper.readValue(message.getPayload(), Requests.Command.class);
      requestId = command.requestId();
      require(command.type() != null, "INVALID_MESSAGE", "消息缺少类型");
      if ("RECONNECT".equals(command.type())) {
        require(!bindings.containsKey(session.getId()), "ALREADY_CONNECTED", "连接已完成身份恢复");
        String id =
            rooms.read(
                command.roomCode(),
                room -> identity.authenticate(room, command.playerToken()).getPlayerId());
        Binding binding = new Binding(command.roomCode(), id, command.playerToken());
        bindings.put(session.getId(), binding);
        rooms.reconnect(command.roomCode(), command.playerToken());
        // New connection supersedes older tabs. Stale sockets cannot keep acting as this player.
        for (var entry : new ArrayList<>(bindings.entrySet()))
          if (!entry.getKey().equals(session.getId())
              && entry.getValue().code().equals(binding.code())
              && entry.getValue().playerId().equals(id)) {
            WebSocketSession old = sessions.get(entry.getKey());
            bindings.remove(entry.getKey());
            if (old != null) {
              send(
                  old,
                  Map.of(
                      "type",
                      "ERROR",
                      "error",
                      new ApiResponse.ApiError("SESSION_REPLACED", "你已在另一个页面连接此房间")));
              old.close(CloseStatus.NORMAL);
            }
          }
        snapshot(session, binding, "PLAYER_RECONNECTED", requestId);
        return;
      }
      Binding binding = bindings.get(session.getId());
      require(binding != null, "UNAUTHENTICATED", "请先恢复房间身份");
      require(Objects.equals(command.roomCode(), binding.code()), "INVALID_ROOM", "房间不匹配");
      if (command.playerId() != null)
        require(command.playerId().equals(binding.playerId()), "INVALID_PLAYER", "玩家身份不匹配");
      if ("HEARTBEAT".equals(command.type())) {
        rooms.heartbeat(binding.code(), binding.token());
        send(session, Map.of("type", "HEARTBEAT", "serverTime", System.currentTimeMillis()));
        return;
      }
      require("GAME_ACTION".equals(command.type()), "INVALID_MESSAGE", "不支持的消息类型");
      rooms.heartbeat(binding.code(), binding.token());
      games.command(binding.code(), binding.token(), command);
      if (!Set.of("LEAVE_ROOM", "CLOSE_ROOM").contains(command.action()))
        snapshot(session, binding, "ACK", requestId);
      else send(session, Map.of("type", "ACK", "requestId", requestId));
    } catch (BusinessException e) {
      error(session, requestId, e.getCode(), e.getMessage());
    } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
      error(session, requestId, "INVALID_MESSAGE", "消息格式错误");
    } catch (Exception e) {
      log.error("WebSocket action failed session={}", session.getId(), e);
      error(session, requestId, "SERVICE_UNAVAILABLE", "连接暂时不可用，请稍后重试");
    }
  }

  @EventListener
  public void changed(RoomChanged event) {
    for (var entry : bindings.entrySet())
      if (entry.getValue().code().equals(event.roomCode())) {
        WebSocketSession session = sessions.get(entry.getKey());
        if (session == null) continue;
        try {
          snapshot(session, entry.getValue(), event.type(), null);
        } catch (BusinessException e) {
          error(session, null, e.getCode(), e.getMessage());
          bindings.remove(entry.getKey());
          try {
            session.close(CloseStatus.NORMAL);
          } catch (Exception ignored) {
          }
        } catch (Exception e) {
          log.warn("Broadcast failed session={}", entry.getKey());
        }
      }
  }

  private void snapshot(WebSocketSession session, Binding binding, String type, String requestId) {
    RoomView view = views.get(binding.code(), binding.token());
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("type", type);
    envelope.put("roomCode", binding.code());
    envelope.put("gameId", view.currentGameId());
    envelope.put("version", view.version());
    envelope.put("serverTime", System.currentTimeMillis());
    envelope.put("data", view);
    if (requestId != null) envelope.put("requestId", requestId);
    send(session, envelope);
  }

  private void error(WebSocketSession session, String requestId, String code, String message) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("type", "ERROR");
    value.put("error", new ApiResponse.ApiError(code, message));
    if (requestId != null) value.put("requestId", requestId);
    send(session, value);
  }

  private void send(WebSocketSession session, Object value) {
    if (!session.isOpen()) return;
    try {
      synchronized (session) {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(value)));
      }
    } catch (Exception e) {
      try {
        session.close(CloseStatus.SERVER_ERROR);
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
    Binding b = bindings.remove(session.getId());
    if (b != null)
      rooms.disconnect(
          b.code(),
          b.playerId(),
          () ->
              bindings.values().stream()
                  .noneMatch(
                      other ->
                          other.code().equals(b.code()) && other.playerId().equals(b.playerId())));
  }

  @Scheduled(fixedDelay = 10000)
  public void expireUnauthenticated() {
    for (WebSocketSession session : sessions.values())
      if (!bindings.containsKey(session.getId())
          && System.currentTimeMillis() - (long) session.getAttributes().get("createdAt") > 15000) {
        try {
          session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
        }
      }
  }
}
