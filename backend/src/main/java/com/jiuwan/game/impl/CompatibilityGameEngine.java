package com.jiuwan.game.impl;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CompatibilityGameEngine extends AbstractGameEngine {
  private final QuestionBank questions;

  public CompatibilityGameEngine(RandomSource random, QuestionBank questions) {
    super(random);
    this.questions = questions;
  }

  public String gameId() {
    return "compatibility";
  }

  public String gameName() {
    return "默契测试";
  }

  protected void initialize(GameContext c) {
    List<String> ids = new ArrayList<>(c.state().getParticipants());
    String first = ids.remove(random.nextInt(ids.size()));
    String second = ids.get(random.nextInt(ids.size()));
    c.metadata().put("selectedIds", List.of(first, second));
    var q = questions.compatibility(random.nextInt(10000));
    c.metadata().put("question", q.get("question").asText());
    c.metadata()
        .put(
            "options", List.of(q.get("options").get(0).asText(), q.get("options").get(1).asText()));
  }

  public GameActionResult handleAction(GameContext c, Player p, GameAction a) {
    validate(c, p, a.action(), "ANSWER");
    require(
        ((List<?>) c.metadata().get("selectedIds")).contains(p.getPlayerId()),
        "NOT_SELECTED",
        "本轮请为两位玩家加油");
    String answer = a.value("answer");
    require(((List<?>) c.metadata().get("options")).contains(answer), "INVALID_ANSWER", "请选择给定选项");
    choose(c, p, answer);
    if (c.state().getPrivateChoices().size() == 2) {
      boolean match = new HashSet<>(c.state().getPrivateChoices().values()).size() == 1;
      c.metadata().put("answers", new LinkedHashMap<>(c.state().getPrivateChoices()));
      c.metadata().put("matched", match);
      c.state().setComplete(true);
      List<String> ids = new ArrayList<>(c.state().getPrivateChoices().keySet());
      if (match) {
        ids.forEach(
            id -> {
              Player player = c.room().getPlayers().get(id);
              player.setScore(player.getScore() + 1);
            });
        event(c, GameEvent.Type.RoundWinnerEvent, ids);
      } else event(c, GameEvent.Type.RoundLoserEvent, ids);
    }
    return new GameActionResult(isFinished(c));
  }

  @Override
  public void timeout(GameContext c) {
    super.timeout(c);
    c.metadata().put("cancelled", true);
  }
}
