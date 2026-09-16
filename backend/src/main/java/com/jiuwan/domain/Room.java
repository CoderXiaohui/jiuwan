package com.jiuwan.domain;

import com.jiuwan.game.GameState;
import java.util.*;
import lombok.Data;

@Data
public class Room {
  public enum Status {
    WAITING,
    PLAYING,
    FINISHED,
    CLOSED
  }

  private String roomId;
  private String roomCode;
  private String ownerId;
  private Status status = Status.WAITING;
  private String selectedGameId;
  private String currentGameId;
  // Most recently started game, retained when returning to the lobby.
  private String lastGameId;
  private long createdAt;
  private long updatedAt;
  private long gameStateVersion;
  private Map<String, Player> players = new LinkedHashMap<>();
  private RoomSettings settings = new RoomSettings();
  private GameState gameState;
  private Set<String> processedRequests = new LinkedHashSet<>();
}
