package com.jiuwan.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.Room;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "jiuwan.storage.mode", havingValue = "redis", matchIfMissing = true)
public class RedisRoomRepository implements RoomRepository {
  private final StringRedisTemplate redis;
  private final RoomSnapshotCodec codec;
  private static final String PREFIX = "jiuwan:room:";

  public RedisRoomRepository(StringRedisTemplate redis, ObjectMapper mapper) {
    this.redis = redis;
    this.codec = new RoomSnapshotCodec(mapper);
  }

  public Room find(String code) {
    return codec.decode(redis.opsForValue().get(PREFIX + code));
  }

  public boolean create(Room room) {
    return Boolean.TRUE.equals(
        redis
            .opsForValue()
            .setIfAbsent(
                PREFIX + room.getRoomCode(), codec.encode(room), RoomSnapshotCodec.ttl(room)));
  }

  public void save(Room room) {
    redis
        .opsForValue()
        .set(PREFIX + room.getRoomCode(), codec.encode(room), RoomSnapshotCodec.ttl(room));
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
