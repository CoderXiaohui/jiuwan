package com.jiuwan.game;

import java.util.*;
import lombok.Data;

/** Server-only poker state. Views must explicitly select fields; never serialize this to clients. */
@Data
public class ZhaJinHuaState {
  private Map<String, Seat> seats = new LinkedHashMap<>();
  private String currentPlayerId;
  private int turnNumber = 1;
  private int actionLimit;
  private int baseStake = 1;
  private int pot;
  private boolean special235;
  private boolean drinkMode;
  private String winnerId;
  private List<Move> moves = new ArrayList<>();

  @Data
  public static class Seat {
    private List<Integer> cards = new ArrayList<>();
    private boolean seen;
    private String status = "active";
    private int contribution = 1;
  }

  public record Move(String playerId, String action, int points, String targetId, String loserId) {}
}
