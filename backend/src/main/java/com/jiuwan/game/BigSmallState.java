package com.jiuwan.game;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/** Server-only dealt cards. Client views must be built for each viewer. */
@Data
public class BigSmallState {
  private Map<String, Integer> cards = new LinkedHashMap<>();
}
