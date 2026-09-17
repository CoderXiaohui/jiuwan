package com.jiuwan.service;

import static com.jiuwan.exception.BusinessException.require;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.*;
import com.jiuwan.dto.Requests;
import com.jiuwan.game.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {
  private final RoomService rooms;
  private final GameRegistry registry;
  private final ObjectMapper mapper;
  private final IdentityService identity;

  public void start(String code, String token, String gameId, String requestId) {
    rooms.command(
        code,
        token,
        requestId,
        (room, player) -> {
          rooms.owner(room, player);
          require(room.getStatus() != Room.Status.PLAYING, "GAME_IN_PROGRESS", "请先结束当前游戏");
          var engine = registry.get(gameId);
          engine.start(new GameContext(room));
          room.setSelectedGameId(gameId);
          room.setCurrentGameId(gameId);
          room.setStatus(Room.Status.PLAYING);
          log.info("Game started code={} game={}", code, gameId);
        },
        "GAME_STARTED");
  }

  public void command(String code, String token, Requests.Command command) {
    String action = command.action();
    require(action != null, "INVALID_ACTION", "操作不能为空");
    if ("START_GAME".equals(action)) {
      start(code, token, command.gameId(), command.requestId());
      return;
    }
    String event =
        switch (action) {
          case "LEAVE_ROOM", "KICK_PLAYER" -> "PLAYER_LEFT";
          case "END_GAME" -> "GAME_ENDED";
          case "NEXT_ROUND" -> "GAME_CHANGED";
          case "SELECT_GAME" -> "ROOM_STATE_UPDATE";
          default -> "GAME_STATE_UPDATE";
        };
    rooms.command(
        code,
        token,
        command.requestId(),
        (room, player) -> {
          switch (action) {
            case "SELECT_GAME" -> {
              rooms.owner(room, player);
              require(room.getStatus() != Room.Status.PLAYING, "GAME_IN_PROGRESS", "请返回大厅选择游戏");
              registry.get(command.gameId());
              room.setSelectedGameId(command.gameId());
            }
            case "LEAVE_ROOM" -> rooms.leave(room, player, player.getPlayerId());
            case "KICK_PLAYER" ->
                rooms.leave(
                    room, player, new GameAction(action, command.data()).value("targetPlayerId"));
            case "CLOSE_ROOM" -> {
              rooms.owner(room, player);
              room.setStatus(Room.Status.CLOSED);
            }
            case "UPDATE_PROFILE" -> {
              var data = new GameAction(action, command.data());
              identity.update(
                  player, new Requests.Profile(data.value("nickname"), data.value("avatar")));
            }
            case "UPDATE_SETTINGS" -> {
              rooms.owner(room, player);
              require(room.getStatus() != Room.Status.PLAYING, "GAME_IN_PROGRESS", "请返回大厅修改设置");
              if (command.data() != null && command.data().containsKey("angryBirdsBombCount")) {
                var count = mapper.valueToTree(command.data().get("angryBirdsBombCount"));
                require(count.isIntegralNumber() && count.canConvertToInt()
                        && count.intValue() >= 1 && count.intValue() <= 6,
                    "INVALID_SETTINGS", "炸弹鸟数量需为 1–6 的整数");
              }
              RoomSettings settings = mapper.convertValue(command.data(), RoomSettings.class);
              require(
                  settings.getMaxPlayers() >= 2
                      && settings.getMaxPlayers() <= 20
                      && settings.getMaxPlayers() >= room.getPlayers().size(),
                  "INVALID_SETTINGS",
                  "人数上限需为 2–20 且不少于当前人数");
              require(
                  settings.getGameMode() != null
                      && List.of("normal", "friends", "couple", "mellow")
                          .contains(settings.getGameMode()),
                  "INVALID_SETTINGS",
                  "无效的题库模式");
              require(
                  settings.getDiceRule() != null
                      && List.of("lowest", "highest").contains(settings.getDiceRule()),
                  "INVALID_SETTINGS",
                  "无效的骰子规则");
              require(
                  settings.getPunishmentMode() != null
                      && List.of("challenge", "truth", "dare")
                          .contains(settings.getPunishmentMode()),
                  "INVALID_SETTINGS",
                  "无效的挑战方式");
              room.setSettings(settings);
            }
            case "END_GAME" -> {
              rooms.owner(room, player);
              require(room.getStatus() == Room.Status.PLAYING, "NO_GAME", "当前没有进行中的游戏");
              room.setStatus(Room.Status.FINISHED);
              room.setCurrentGameId(null);
              room.setGameState(null);
              log.info("Game ended code={}", code);
            }
            default -> {
              require(
                  room.getStatus() == Room.Status.PLAYING && room.getGameState() != null,
                  "NO_GAME",
                  "游戏还没有开始");
              require(
                  Objects.equals(command.gameId(), room.getCurrentGameId())
                      && Objects.equals(command.instanceId(), room.getGameState().getInstanceId()),
                  "STALE_ACTION",
                  "局次已变化，请按最新画面操作");
              var engine = registry.get(room.getCurrentGameId());
              var context = new GameContext(room);
              if ("NEXT_ROUND".equals(action)) {
                rooms.owner(room, player);
                require(engine.isFinished(context), "ROUND_ACTIVE", "请等待本轮结束");
                engine.nextRound(context);
              } else engine.handleAction(context, player, new GameAction(action, command.data()));
            }
          }
        },
        event);
  }

  public void timeout(Room room) {
    registry.get(room.getCurrentGameId()).timeout(new GameContext(room));
  }
}
