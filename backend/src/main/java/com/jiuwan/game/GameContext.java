package com.jiuwan.game;

import com.jiuwan.domain.*;
import java.util.*;

public record GameContext(Room room) {
  public GameState state() {
    return room.getGameState();
  }

  public List<Player> players() {
    return new ArrayList<>(room.getPlayers().values());
  }

  public int round() {
    return state().getRound();
  }

  public Map<String, Object> metadata() {
    return state().getPublicData();
  }
}
