package com.jiuwan.game.impl;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DiceGameEngine extends AbstractGameEngine {
  public DiceGameEngine(RandomSource random) {
    super(random);
  }

  public String gameId() {
    return "dice";
  }

  public String gameName() {
    return "摇骰子";
  }

  protected void initialize(GameContext c) {
    c.metadata().put("rule", c.room().getSettings().getDiceRule());
  }

  public GameActionResult handleAction(GameContext c, Player p, GameAction a) {
    validate(c, p, a.action(), "SHAKE_DICE");
    choose(c, p, String.valueOf(random.nextInt(6) + 1));
    if (c.state().getPrivateChoices().size() == c.state().getParticipants().size()) reveal(c);
    return new GameActionResult(isFinished(c));
  }

  private void reveal(GameContext c) {
    Map<String, Integer> rolls = new LinkedHashMap<>();
    c.state().getPrivateChoices().forEach((id, value) -> rolls.put(id, Integer.parseInt(value)));
    var scores = rolls.values().stream().mapToInt(Integer::intValue);
    int losing =
        "highest".equals(c.metadata().get("rule"))
            ? scores.max().orElse(0)
            : scores.min().orElse(0);
    List<String> losers = rolls.keySet().stream().filter(id -> rolls.get(id) == losing).toList();
    c.metadata().put("rolls", rolls);
    c.metadata().put("loserIds", losers);
    c.state().setComplete(true);
    if (!losers.isEmpty()) event(c, GameEvent.Type.RoundLoserEvent, losers);
  }

  @Override
  public void timeout(GameContext c) {
    reveal(c);
    c.metadata().put("timedOut", true);
  }
}
