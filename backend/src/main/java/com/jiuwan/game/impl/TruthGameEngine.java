package com.jiuwan.game.impl;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TruthGameEngine extends AbstractGameEngine {
  private final QuestionBank questions;

  public TruthGameEngine(RandomSource random, QuestionBank questions) {
    super(random);
    this.questions = questions;
  }

  public String gameId() {
    return "truth";
  }

  public String gameName() {
    return "真心话 / 大冒险";
  }

  protected void initialize(GameContext c) {
    String id = c.state().getParticipants().get(random.nextInt(c.state().getParticipants().size()));
    c.metadata().put("selectedIds", List.of(id));
    c.metadata().put("mode", c.room().getSettings().getGameMode());
    event(c, GameEvent.Type.PlayerSelectedEvent, List.of(id));
  }

  public GameActionResult handleAction(GameContext c, Player p, GameAction a) {
    validate(c, p, a.action(), "DRAW");
    require(
        ((List<?>) c.metadata().get("selectedIds")).contains(p.getPlayerId()),
        "NOT_SELECTED",
        "请等待被选中的玩家抽卡");
    String type = a.value("type");
    require(List.of("truth", "dare").contains(type), "INVALID_TYPE", "请选择真心话或大冒险");
    choose(c, p, type);
    c.metadata().put("cardType", type);
    c.metadata()
        .put(
            "question",
            questions.truth(c.room().getSettings().getGameMode(), type, random.nextInt(10000)));
    c.state().setComplete(true);
    return new GameActionResult(true);
  }
}
