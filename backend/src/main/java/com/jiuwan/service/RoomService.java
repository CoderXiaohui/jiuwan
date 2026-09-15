package com.jiuwan.service;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.*;
import com.jiuwan.dto.*;
import com.jiuwan.game.RandomSource;
import com.jiuwan.repository.RoomRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RoomService {
  private final RoomRepository repository;
  private final IdentityService identity;
  private final RandomSource random;
  private final ApplicationEventPublisher publisher;
  private final Object[] locks = new Object[128];
  private final Set<String> active = ConcurrentHashMap.newKeySet();

  public RoomService(
      RoomRepository repository,
      IdentityService identity,
      RandomSource random,
      ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.identity = identity;
    this.random = random;
    this.publisher = publisher;
    Arrays.setAll(locks, i -> new Object());
  }

  private Object lock(String code) {
    return locks[Math.floorMod(Objects.hashCode(code), locks.length)];
  }

  public Set<String> activeCodes() {
    return Set.copyOf(active);
  }

  public void recover() {
    for (String code : repository.codes())
      synchronized (lock(code)) {
        Room room = repository.find(code);
        if (room == null || room.getStatus() == Room.Status.CLOSED) continue;
        room.getPlayers()
            .values()
            .forEach(
                p -> {
                  p.setConnected(false);
                  p.setLastSeen(System.currentTimeMillis());
                });
        repository.save(room);
        active.add(code);
      }
  }

  public RoomView.Credentials create(Requests.Profile profile) {
    String token = identity.token();
    Player player = identity.create(profile, token);
    for (int attempt = 0; attempt < 50; attempt++) {
      String code = String.valueOf(100000 + random.nextInt(900000));
      synchronized (lock(code)) {
        Room room = new Room();
        room.setRoomId(UUID.randomUUID().toString());
        room.setRoomCode(code);
        room.setOwnerId(player.getPlayerId());
        room.setCreatedAt(System.currentTimeMillis());
        room.setUpdatedAt(room.getCreatedAt());
        room.setGameStateVersion(1);
        room.getPlayers().put(player.getPlayerId(), player);
        if (repository.create(room)) {
          active.add(code);
          log.info("Room created code={} player={}", code, player.getPlayerId());
          publisher.publishEvent(new RoomChanged(code, "ROOM_CREATED"));
          return new RoomView.Credentials(code, player.getPlayerId(), token);
        }
      }
    }
    throw new com.jiuwan.exception.BusinessException("BUSY", "房间繁忙，请稍后再试");
  }

  public RoomView.Credentials join(String code, Requests.Profile profile) {
    return mutate(
        code,
        room -> {
          require(
              room.getStatus() != Room.Status.PLAYING, "GAME_IN_PROGRESS", "游戏进行中，请等本轮结束返回大厅后加入");
          require(
              room.getPlayers().size() < room.getSettings().getMaxPlayers(), "ROOM_FULL", "房间已经满员");
          String token = identity.token();
          Player p = identity.create(profile, token);
          room.getPlayers().put(p.getPlayerId(), p);
          log.info("Player joined code={} player={}", code, p.getPlayerId());
          return new RoomView.Credentials(code, p.getPlayerId(), token);
        },
        "PLAYER_JOINED");
  }

  private Room load(String code) {
    require(code != null && code.matches("[0-9]{6}"), "INVALID_ROOM", "请输入 6 位房间码");
    Room room = repository.find(code);
    require(room != null, "ROOM_NOT_FOUND", "房间不存在或已过期");
    require(room.getStatus() != Room.Status.CLOSED, "ROOM_CLOSED", "房间已关闭");
    return room;
  }

  public <T> T read(String code, Function<Room, T> operation) {
    synchronized (lock(code)) {
      return operation.apply(load(code));
    }
  }

  public <T> T mutate(String code, Function<Room, T> operation, String type) {
    synchronized (lock(code)) {
      Room room = load(code);
      T result = operation.apply(room);
      save(room, type);
      return result;
    }
  }

  private void save(Room room, String type) {
    room.setUpdatedAt(System.currentTimeMillis());
    room.setGameStateVersion(room.getGameStateVersion() + 1);
    repository.save(room);
    if (room.getStatus() == Room.Status.CLOSED) active.remove(room.getRoomCode());
    else active.add(room.getRoomCode());
    if (type != null) {
      String eventType =
          "GAME_STATE_UPDATE".equals(type)
                  && room.getGameState() != null
                  && room.getGameState().isComplete()
              ? "GAME_RESULT"
              : type;
      publisher.publishEvent(new RoomChanged(room.getRoomCode(), eventType));
    }
  }

  public String reconnect(String code, String token) {
    return mutate(
        code,
        room -> {
          Player p = identity.authenticate(room, token);
          p.setConnected(true);
          p.setLastSeen(System.currentTimeMillis());
          log.info("Player reconnected code={} player={}", code, p.getPlayerId());
          return p.getPlayerId();
        },
        "PLAYER_RECONNECTED");
  }

  public void heartbeat(String code, String token) {
    mutate(
        code,
        room -> {
          Player p = identity.authenticate(room, token);
          p.setLastSeen(System.currentTimeMillis());
          return null;
        },
        null);
  }

  public void disconnect(String code, String playerId) {
    disconnect(code, playerId, () -> true);
  }

  public void disconnect(String code, String playerId, BooleanSupplier noActiveConnection) {
    try {
      mutate(
          code,
          room -> {
            Player p = room.getPlayers().get(playerId);
            if (p != null && noActiveConnection.getAsBoolean()) {
              p.setConnected(false);
              p.setLastSeen(System.currentTimeMillis());
            }
            log.info("Player disconnected code={} player={}", code, playerId);
            return null;
          },
          "ROOM_STATE_UPDATE");
    } catch (com.jiuwan.exception.BusinessException ignored) {
      /* closed or expired room */
    }
  }

  public void owner(Room room, Player p) {
    require(room.getOwnerId().equals(p.getPlayerId()), "OWNER_ONLY", "这个操作需要房主完成");
  }

  public void command(
      String code,
      String token,
      String requestId,
      BiConsumer<Room, Player> operation,
      String type) {
    synchronized (lock(code)) {
      Room room = load(code);
      Player player = identity.authenticate(room, token);
      require(
          requestId != null && !requestId.isBlank() && requestId.length() <= 80,
          "INVALID_REQUEST",
          "操作缺少有效编号");
      String key = player.getPlayerId() + ":" + requestId;
      if (room.getProcessedRequests().contains(key)) return;
      operation.accept(room, player);
      room.getProcessedRequests().add(key);
      if (room.getProcessedRequests().size() > 256)
        room.getProcessedRequests().remove(room.getProcessedRequests().iterator().next());
      save(room, type);
    }
  }

  public void leave(Room room, Player actor, String target) {
    if (!actor.getPlayerId().equals(target)) owner(room, actor);
    require(room.getPlayers().containsKey(target), "INVALID_PLAYER", "玩家已离开");
    room.getPlayers().remove(target);
    if (room.getStatus() == Room.Status.PLAYING) {
      room.setStatus(Room.Status.WAITING);
      room.setCurrentGameId(null);
      room.setGameState(null);
    }
    if (room.getPlayers().isEmpty()) room.setStatus(Room.Status.CLOSED);
    else if (target.equals(room.getOwnerId())) transfer(room);
    log.info("Player left code={} player={}", room.getRoomCode(), target);
  }

  private void transfer(Room room) {
    room.getPlayers().values().stream()
        .filter(Player::isConnected)
        .min(Comparator.comparingLong(Player::getJoinedAt))
        .or(
            () ->
                room.getPlayers().values().stream()
                    .min(Comparator.comparingLong(Player::getJoinedAt)))
        .ifPresent(p -> room.setOwnerId(p.getPlayerId()));
  }

  public void maintain(String code, long now, Consumer<Room> gameTick) {
    synchronized (lock(code)) {
      Room room = repository.find(code);
      if (room == null || room.getStatus() == Room.Status.CLOSED) {
        active.remove(code);
        return;
      }
      boolean changed = false;
      for (Player p : room.getPlayers().values())
        if (p.isConnected() && now - p.getLastSeen() > 65000) {
          p.setConnected(false);
          changed = true;
        }
      Player owner = room.getPlayers().get(room.getOwnerId());
      if (owner != null
          && !owner.isConnected()
          && now - owner.getLastSeen() > 15000
          && room.getPlayers().values().stream().anyMatch(Player::isConnected)) {
        transfer(room);
        changed = true;
      }
      if (room.getPlayers().values().stream().noneMatch(Player::isConnected)
          && room.getPlayers().values().stream().allMatch(p -> now - p.getLastSeen() > 1800000)) {
        room.setStatus(Room.Status.CLOSED);
        changed = true;
      }
      if (room.getStatus() == Room.Status.PLAYING
          && !room.getGameState().isComplete()
          && room.getGameState().getDeadline() > 0
          && now >= room.getGameState().getDeadline()) {
        gameTick.accept(room);
        changed = true;
      }
      if (changed) save(room, "ROOM_STATE_UPDATE");
    }
  }
}
