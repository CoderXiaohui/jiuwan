package com.jiuwan.domain;

import lombok.Data;

@Data
public class RoomSettings {
  private String punishmentMode = "challenge";
  private String gameMode = "normal";
  private int maxPlayers = 12;
  private boolean anonymousVote = true;
  private boolean safeMode = true;
  private String diceRule = "lowest";
  private boolean zhaJinHua235 = false;
  private boolean zhaJinHuaDrink = true;
  private int angryBirdsBombCount = 1;
}
