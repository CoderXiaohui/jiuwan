package com.jiuwan.game.impl;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RouletteGameEngine extends AbstractGameEngine {
  public static final List<String> OPTIONS =
      List.of("自己接受挑战", "左边的人", "右边的人", "指定一人", "全体", "免死金牌", "再来一次");

  public RouletteGameEngine(RandomSource random) {
    super(random);
  }

  public String gameId() {
    return "roulette";
  }

  public String gameName() {
    return "幸运轮盘";
  }

  protected void initialize(GameContext c) {
    c.metadata().put("options", OPTIONS);
    c.metadata().put("spinnerId", c.room().getOwnerId());
  }

  public GameActionResult handleAction(GameContext c, Player p, GameAction a) {
    if ("SELECT_PLAYER".equals(a.action())) {
      validate(c, p, a.action(), "SELECT_PLAYER");
      require(p.getPlayerId().equals(c.metadata().get("spinnerId")), "NOT_SELECTED", "请等待转盘玩家选择");
      require(
          Boolean.TRUE.equals(c.metadata().get("needsSelection")), "INVALID_ACTION", "本轮无需指定玩家");
      String target = a.value("targetPlayerId");
      require(c.state().getParticipants().contains(target), "INVALID_TARGET", "请选择本轮玩家");
      finish(c, List.of(target));
      c.metadata().put("needsSelection", false);
      return new GameActionResult(true);
    }
    validate(c, p, a.action(), "SPIN");
    require(p.getPlayerId().equals(c.metadata().get("spinnerId")), "NOT_SELECTED", "请等待转盘玩家操作");
    choose(c, p, "spin");
    int index = random.nextInt(OPTIONS.size());
    c.metadata().put("resultIndex", index);
    c.metadata().put("result", OPTIONS.get(index));
    List<String> ids = c.state().getParticipants();
    int self = ids.indexOf(p.getPlayerId());
    switch (index) {
      case 0 -> finish(c, List.of(p.getPlayerId()));
      case 1 -> finish(c, List.of(ids.get(Math.floorMod(self - 1, ids.size()))));
      case 2 -> finish(c, List.of(ids.get((self + 1) % ids.size())));
      case 3 -> {
        c.metadata().put("needsSelection", true);
        c.state().setDeadline(System.currentTimeMillis() + 30000);
      }
      case 4 -> {
        c.metadata().put("selectedIds", ids);
        event(c, GameEvent.Type.AllPlayersEvent, ids);
        c.state().setComplete(true);
      }
      default -> {
        c.metadata().put("selectedIds", List.of());
        c.state().setComplete(true);
      }
    }
    return new GameActionResult(isFinished(c));
  }

  private void finish(GameContext c, List<String> ids) {
    c.metadata().put("selectedIds", ids);
    event(c, GameEvent.Type.PlayerPunishedEvent, ids);
    c.state().setComplete(true);
  }

  @Override
  public void timeout(GameContext c) {
    super.timeout(c);
    c.metadata().put("needsSelection", false);
  }
}
