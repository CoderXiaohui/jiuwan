package com.jiuwan.game;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;

/** Server-only board. Never serialize this object directly into a client view. */
@Data
public class AngryBirdsState {
  private int bombCount;
  private Set<Integer> bombIds = new LinkedHashSet<>();
  private Set<Integer> flownIds = new LinkedHashSet<>();
  private int turnNumber = 1;
  private String loserId;
  private Move lastMove;

  public record Move(int birdId, String playerId, int turnNumber, boolean automatic, long at) {}
}
