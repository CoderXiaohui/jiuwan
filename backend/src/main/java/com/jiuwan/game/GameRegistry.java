package com.jiuwan.game;

import com.jiuwan.exception.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class GameRegistry {
  private final Map<String, GameEngine> engines = new LinkedHashMap<>();

  public GameRegistry(List<GameEngine> plugins) {
    for (GameEngine engine : plugins) {
      if (engines.putIfAbsent(engine.gameId(), engine) != null)
        throw new IllegalStateException("Duplicate game: " + engine.gameId());
    }
  }

  public GameEngine get(String id) {
    GameEngine engine = engines.get(id);
    if (engine == null) throw new BusinessException("INVALID_GAME", "这个游戏还未上线");
    return engine;
  }

  public List<Map<String, Object>> list() {
    return engines.values().stream()
        .map(
            e ->
                Map.<String, Object>of(
                    "gameId", e.gameId(), "gameName", e.gameName(), "minPlayers", 2))
        .toList();
  }
}
