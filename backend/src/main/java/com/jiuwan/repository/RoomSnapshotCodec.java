package com.jiuwan.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.Room;
import java.time.Duration;
import lombok.RequiredArgsConstructor;

/** Both stores commit detached snapshots with the same expiry rules. */
@RequiredArgsConstructor
final class RoomSnapshotCodec {
  private final ObjectMapper mapper;

  static Duration ttl(Room room) {
    return Duration.ofMinutes(room.getStatus() == Room.Status.CLOSED ? 15 : 360);
  }

  String encode(Room room) {
    try {
      return mapper.writeValueAsString(room);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  Room decode(String value) {
    if (value == null) return null;
    try {
      return mapper.readValue(value, Room.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
