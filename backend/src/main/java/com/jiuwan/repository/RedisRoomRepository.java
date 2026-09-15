package com.jiuwan.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.Room;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisRoomRepository implements RoomRepository {
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private static final String PREFIX = "jiuwan:room:";

  private Duration ttl(Room room) {
    return Duration.ofMinutes(room.getStatus() == Room.Status.CLOSED ? 15 : 360);
  }

  private String encode(Room room) {
    try {
      return mapper.writeValueAsString(room);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public Room find(String code) {
    String value = redis.opsForValue().get(PREFIX + code);
    if (value == null) return null;
    try {
      return mapper.readValue(value, Room.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public boolean create(Room room) {
    return Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent(PREFIX + room.getRoomCode(), encode(room), ttl(room)));
  }

  public void save(Room room) {
    redis.opsForValue().set(PREFIX + room.getRoomCode(), encode(room), ttl(room));
  }

  public Set<String> codes() {
    Set<String> codes = new HashSet<>();
    try (Cursor<String> cursor =
        redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(100).build())) {
      cursor.forEachRemaining(key -> codes.add(key.substring(PREFIX.length())));
    }
    return codes;
  }
}
