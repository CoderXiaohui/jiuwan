package com.jiuwan.game;

import java.util.*;
import lombok.Data;

@Data
public class GameState {
  private String gameId;
  private String instanceId;
  private int round = 1;
  private boolean complete;
  private long startsAt;
  private long deadline;
  private List<String> participants = new ArrayList<>();
  private Map<String, String> privateChoices = new LinkedHashMap<>();
  private ZhaJinHuaState zhaJinHua;
  private BigSmallState bigSmall;
  private AngryBirdsState angryBirds;
  private Map<String, Object> publicData = new LinkedHashMap<>();
  private List<GameEvent> events = new ArrayList<>();
}
