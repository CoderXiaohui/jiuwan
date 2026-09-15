package com.jiuwan.game;

import java.util.List;

public record GameEvent(Type type, List<String> playerIds) {
  public enum Type {
    RoundWinnerEvent,
    RoundLoserEvent,
    PlayerSelectedEvent,
    PlayerPunishedEvent,
    AllPlayersEvent,
    GameFinishedEvent
  }
}
