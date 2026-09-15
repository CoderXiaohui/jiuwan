package com.jiuwan.dto;

import com.jiuwan.domain.RoomSettings;
import java.util.*;

public record RoomView(
    String roomId,
    String roomCode,
    String ownerId,
    String status,
    String currentGameId,
    long createdAt,
    long version,
    List<PlayerView> players,
    RoomSettings settings,
    Map<String, Object> game,
    List<Map<String, Object>> events) {
  public record PlayerView(
      String playerId,
      String nickname,
      String avatar,
      boolean connected,
      boolean isOwner,
      long joinedAt,
      int score) {}

  public record Credentials(String roomCode, String playerId, String playerToken) {}
}
