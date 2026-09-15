package com.jiuwan.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.Room;
import com.jiuwan.exception.BusinessException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

public class MemoryRoomRepository implements RoomRepository {
  private record Snapshot(String json, long expiresAt) {}

  private final ConcurrentHashMap<String, Snapshot> rooms = new ConcurrentHashMap<>();
  private final RoomSnapshotCodec codec;
  private final int maxRooms;
  private final Clock clock;

  public MemoryRoomRepository(ObjectMapper mapper, int maxRooms) {
    this(mapper, maxRooms, Clock.systemUTC());
  }

  MemoryRoomRepository(ObjectMapper mapper, int maxRooms, Clock clock) {
    if (maxRooms < 1) throw new IllegalArgumentException("memoryMaxRooms must be positive");
    this.codec = new RoomSnapshotCodec(mapper);
    this.maxRooms = maxRooms;
    this.clock = clock;
  }

  private Snapshot live(String code) {
    Snapshot snapshot = rooms.get(code);
    if (snapshot != null && snapshot.expiresAt() <= clock.millis()) {
      rooms.remove(code, snapshot);
      return null;
    }
    return snapshot;
  }

  @Override
  public Room find(String code) {
    Snapshot snapshot = live(code);
    return snapshot == null ? null : codec.decode(snapshot.json());
  }

  // Serialize commits across room codes so concurrent creates cannot exceed capacity.
  @Override
  public synchronized boolean create(Room room) {
    if (live(room.getRoomCode()) != null) return false;
    ensureCapacity();
    store(room);
    return true;
  }

  @Override
  public synchronized void save(Room room) {
    if (live(room.getRoomCode()) == null) ensureCapacity();
    store(room);
  }

  private void store(Room room) {
    rooms.put(
        room.getRoomCode(),
        new Snapshot(codec.encode(room), clock.millis() + RoomSnapshotCodec.ttl(room).toMillis()));
  }

  private void ensureCapacity() {
    if (rooms.size() >= maxRooms) removeExpired();
    if (rooms.size() >= maxRooms) {
      throw new BusinessException("SERVICE_UNAVAILABLE", "房间容量已满，请稍后重试");
    }
  }

  @Scheduled(fixedDelay = 60000)
  public void removeExpired() {
    long now = clock.millis();
    rooms.forEach(
        (code, snapshot) -> {
          if (snapshot.expiresAt() <= now) rooms.remove(code, snapshot);
        });
  }

  @Override
  public Set<String> codes() {
    removeExpired();
    return Set.copyOf(rooms.keySet());
  }
}
