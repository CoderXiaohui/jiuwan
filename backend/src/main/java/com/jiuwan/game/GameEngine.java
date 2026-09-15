package com.jiuwan.game;

import com.jiuwan.domain.Player;
import java.util.Map;

public interface GameEngine {
  String gameId();

  String gameName();

  void start(GameContext context);

  GameActionResult handleAction(GameContext context, Player player, GameAction action);

  void nextRound(GameContext context);

  Map<String, Object> getPlayerView(GameContext context, Player player);

  Map<String, Object> getPublicView(GameContext context);

  boolean isFinished(GameContext context);

  void timeout(GameContext context);
}
