package com.jiuwan.game;

import java.util.Map;

public record GameAction(String action, Map<String, Object> data) {
  public String value(String key) {
    return data == null ? "" : String.valueOf(data.getOrDefault(key, ""));
  }
}
