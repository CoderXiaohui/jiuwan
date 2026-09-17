package com.jiuwan.game.impl;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AngryBirdsGameEngine extends AbstractGameEngine {
  private static final int BIRD_COUNT = 16;
  private static final int TURN_MILLIS = 10_000;

  public AngryBirdsGameEngine(RandomSource random) { super(random); }

  public String gameId() { return "angry-birds"; }

  public String gameName() { return "愤怒的小鸟"; }

  protected void initialize(GameContext c) {
    var ids = new ArrayList<>(c.state().getParticipants());
    require(ids.size() <= 10, "TOO_MANY_PLAYERS", "愤怒的小鸟最多支持 10 位在线玩家");
    int bombCount = c.room().getSettings().getAngryBirdsBombCount();
    require(bombCount >= 1 && bombCount <= 6, "INVALID_SETTINGS", "炸弹鸟数量需为 1–6 的整数");
    shuffle(ids);
    c.state().setParticipants(ids);
    List<Integer> positions = new ArrayList<>();
    for (int i = 0; i < BIRD_COUNT; i++) positions.add(i);
    shuffle(positions);
    var board = new AngryBirdsState();
    board.setBombCount(bombCount);
    board.setBombIds(new LinkedHashSet<>(positions.subList(0, bombCount)));
    c.state().setAngryBirds(board);
    c.state().setDeadline(c.state().getStartsAt() + TURN_MILLIS);
  }

  private <T> void shuffle(List<T> values) {
    for (int i = values.size() - 1; i > 0; i--) Collections.swap(values, i, random.nextInt(i + 1));
  }

  private String currentPlayer(GameContext c) {
    return c.state().getParticipants().get(
        (c.state().getAngryBirds().getTurnNumber() - 1) % c.state().getParticipants().size());
  }

  public GameActionResult handleAction(GameContext c, Player player, GameAction action) {
    validate(c, player, action.action(), "PICK_BIRD");
    var board = c.state().getAngryBirds();
    require(String.valueOf(board.getTurnNumber()).equals(action.value("turnNumber")),
        "STALE_TURN", "行动顺序已变化，请按最新画面操作");
    require(player.getPlayerId().equals(currentPlayer(c)), "NOT_YOUR_TURN", "还没轮到你点击");
    String value = action.value("birdId");
    require(value.matches("(?:[0-9]|1[0-5])"), "INVALID_BIRD", "请选择棋盘中的小鸟");
    int birdId = Integer.parseInt(value);
    require(!board.getFlownIds().contains(birdId), "BIRD_GONE", "这只小鸟已经飞走了");
    pick(c, birdId, false);
    return new GameActionResult(isFinished(c));
  }

  private void pick(GameContext c, int birdId, boolean automatic) {
    var board = c.state().getAngryBirds();
    String playerId = currentPlayer(c);
    long now = System.currentTimeMillis();
    board.setLastMove(new AngryBirdsState.Move(birdId, playerId, board.getTurnNumber(), automatic, now));
    if (board.getBombIds().contains(birdId)) {
      board.setLoserId(playerId);
      c.state().setComplete(true);
      c.state().setDeadline(0);
    } else {
      board.getFlownIds().add(birdId);
      board.setTurnNumber(board.getTurnNumber() + 1);
      c.state().setDeadline(now + TURN_MILLIS);
    }
  }

  @Override
  public void timeout(GameContext c) {
    if (c.state().isComplete() || System.currentTimeMillis() < c.state().getDeadline()) return;
    List<Integer> remaining = new ArrayList<>();
    for (int i = 0; i < BIRD_COUNT; i++)
      if (!c.state().getAngryBirds().getFlownIds().contains(i)) remaining.add(i);
    pick(c, remaining.get(random.nextInt(remaining.size())), true);
  }

  @Override
  public Map<String, Object> getPublicView(GameContext c) {
    Map<String, Object> view = super.getPublicView(c);
    var board = c.state().getAngryBirds();
    Map<String, Object> display = new LinkedHashMap<>();
    display.put("bombCount", board.getBombCount());
    display.put("currentPlayerId", currentPlayer(c));
    display.put("turnNumber", board.getTurnNumber());
    List<Map<String, Object>> birds = new ArrayList<>();
    for (int i = 0; i < BIRD_COUNT; i++) {
      String status = board.getFlownIds().contains(i) ? "flown" : "hidden";
      if (c.state().isComplete() && board.getBombIds().contains(i))
        status = board.getLastMove().birdId() == i ? "exploded" : "bomb";
      birds.add(Map.of("id", i, "status", status));
    }
    display.put("birds", birds);
    if (board.getLastMove() != null) display.put("lastMove", board.getLastMove());
    if (c.state().isComplete()) display.put("loserId", board.getLoserId());
    view.put("angryBirds", display);
    return view;
  }
}
