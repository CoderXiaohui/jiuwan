package com.jiuwan.game;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import java.util.*;

public abstract class AbstractGameEngine implements GameEngine {
  protected final RandomSource random;

  protected AbstractGameEngine(RandomSource random) {
    this.random = random;
  }

  @Override
  public void start(GameContext context) {
    prepare(context, 1);
  }

  @Override
  public void nextRound(GameContext context) {
    prepare(context, context.round() + 1);
  }

  private void prepare(GameContext context, int round) {
    GameState state = new GameState();
    state.setGameId(gameId());
    state.setRound(round);
    state.setInstanceId(UUID.randomUUID().toString());
    state.setStartsAt(System.currentTimeMillis() + 3000);
    state.setDeadline(state.getStartsAt() + 60000);
    state.setParticipants(
        context.players().stream().filter(Player::isConnected).map(Player::getPlayerId).toList());
    require(state.getParticipants().size() >= 2, "NEED_PLAYERS", "至少需要两位在线玩家");
    context.room().setGameState(state);
    initialize(context);
  }

  protected abstract void initialize(GameContext context);

  protected void validate(GameContext c, Player p, String actual, String expected) {
    require(!c.state().isComplete(), "ROUND_FINISHED", "这一轮已经结束");
    require(System.currentTimeMillis() >= c.state().getStartsAt(), "COUNTDOWN", "倒计时结束后再操作");
    require(System.currentTimeMillis() < c.state().getDeadline(), "ROUND_EXPIRED", "本轮时间已到，正在揭晓结果");
    require(c.state().getParticipants().contains(p.getPlayerId()), "NOT_PARTICIPANT", "你不是本轮参与者");
    require(expected.equals(actual), "INVALID_ACTION", "不支持的游戏操作");
  }

  protected void choose(GameContext c, Player p, String value) {
    require(
        !c.state().getPrivateChoices().containsKey(p.getPlayerId()), "ALREADY_ACTED", "你已经完成本轮操作");
    c.state().getPrivateChoices().put(p.getPlayerId(), value);
  }

  protected void event(GameContext c, GameEvent.Type type, List<String> ids) {
    c.state().getEvents().add(new GameEvent(type, ids));
  }

  @Override
  public boolean isFinished(GameContext c) {
    return c.state().isComplete();
  }

  @Override
  public Map<String, Object> getPublicView(GameContext c) {
    Map<String, Object> view = new LinkedHashMap<>(c.metadata());
    view.put("gameId", gameId());
    view.put("round", c.round());
    view.put("instanceId", c.state().getInstanceId());
    view.put("complete", c.state().isComplete());
    view.put("startsAt", c.state().getStartsAt());
    view.put("deadline", c.state().getDeadline());
    view.put("participants", c.state().getParticipants());
    view.put("submittedCount", c.state().getPrivateChoices().size());
    return view;
  }

  @Override
  public Map<String, Object> getPlayerView(GameContext c, Player p) {
    Map<String, Object> view = getPublicView(c);
    view.put("hasActed", c.state().getPrivateChoices().containsKey(p.getPlayerId()));
    if (c.state().getPrivateChoices().containsKey(p.getPlayerId()))
      view.put("myChoice", c.state().getPrivateChoices().get(p.getPlayerId()));
    return view;
  }

  @Override
  public void timeout(GameContext c) {
    c.state().setComplete(true);
    c.metadata().put("timedOut", true);
  }
}
