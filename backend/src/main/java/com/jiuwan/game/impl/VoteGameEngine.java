package com.jiuwan.game.impl;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class VoteGameEngine extends AbstractGameEngine {
  private final QuestionBank questions;

  public VoteGameEngine(RandomSource random, QuestionBank questions) {
    super(random);
    this.questions = questions;
  }

  public String gameId() {
    return "vote";
  }

  public String gameName() {
    return "匿名投票";
  }

  protected void initialize(GameContext c) {
    c.metadata().put("question", questions.vote(random.nextInt(10000)));
    c.state().setDeadline(c.state().getStartsAt() + 45000);
  }

  public GameActionResult handleAction(GameContext c, Player p, GameAction a) {
    validate(c, p, a.action(), "VOTE");
    String target = a.value("targetPlayerId");
    require(c.state().getParticipants().contains(target), "INVALID_TARGET", "请选择本轮玩家");
    choose(c, p, target);
    if (c.state().getPrivateChoices().size() == c.state().getParticipants().size()) reveal(c);
    return new GameActionResult(isFinished(c));
  }

  private void reveal(GameContext c) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    c.state().getParticipants().forEach(id -> counts.put(id, 0));
    c.state()
        .getPrivateChoices()
        .values()
        .forEach(id -> counts.computeIfPresent(id, (k, v) -> v + 1));
    int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    List<String> winners =
        max == 0
            ? List.of()
            : counts.keySet().stream().filter(id -> counts.get(id) == max).toList();
    c.metadata().put("counts", counts);
    c.metadata().put("selectedIds", winners);
    c.state().setComplete(true);
    if (!c.room().getSettings().isAnonymousVote())
      c.metadata().put("ballots", new LinkedHashMap<>(c.state().getPrivateChoices()));
    if (!winners.isEmpty()) event(c, GameEvent.Type.PlayerPunishedEvent, winners);
  }

  @Override
  public void timeout(GameContext c) {
    reveal(c);
    c.metadata().put("timedOut", true);
  }
}
