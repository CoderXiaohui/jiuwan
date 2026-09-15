package com.jiuwan.domain;

import lombok.Data;

@Data
public class Player {
  private String playerId;
  private String tokenHash;
  private String nickname;
  private String avatar;
  private boolean connected;
  private long joinedAt;
  private long lastSeen;
  private int score;
}
