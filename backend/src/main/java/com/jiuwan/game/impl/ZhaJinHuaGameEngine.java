package com.jiuwan.game.impl;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ZhaJinHuaGameEngine extends AbstractGameEngine {
  private static final int TURN_MILLIS = 30_000;
  private static final int MAX_STAKE = 5;

  public ZhaJinHuaGameEngine(RandomSource random) { super(random); }

  public String gameId() { return "zhajinhua"; }

  public String gameName() { return "炸金花"; }

  protected void initialize(GameContext c) {
    var ids = c.state().getParticipants();
    require(ids.size() <= 17, "TOO_MANY_PLAYERS", "炸金花使用一副牌，最多支持 17 位在线玩家");
    var poker = new ZhaJinHuaState();
    poker.setSpecial235(c.room().getSettings().isZhaJinHua235());
    poker.setDrinkMode(c.room().getSettings().isZhaJinHuaDrink());
    poker.setActionLimit(ids.size() * 5);
    poker.setPot(ids.size());
    poker.setCurrentPlayerId(ids.get((c.round() - 1) % ids.size()));
    List<Integer> deck = new ArrayList<>();
    for (int card = 0; card < 52; card++) deck.add(card);
    for (int i = deck.size() - 1; i > 0; i--) Collections.swap(deck, i, random.nextInt(i + 1));
    for (String id : ids) poker.getSeats().put(id, new ZhaJinHuaState.Seat());
    int index = 0;
    for (int card = 0; card < 3; card++)
      for (String id : ids) poker.getSeats().get(id).getCards().add(deck.get(index++));
    c.state().setZhaJinHua(poker);
    c.state().setDeadline(c.state().getStartsAt() + TURN_MILLIS);
  }

  public GameActionResult handleAction(GameContext c, Player player, GameAction action) {
    require(List.of("LOOK", "CALL", "RAISE", "COMPARE", "FOLD").contains(action.action()),
        "INVALID_ACTION", "不支持的炸金花操作");
    validate(c, player, action.action(), action.action());
    var poker = c.state().getZhaJinHua();
    String id = player.getPlayerId();
    var seat = poker.getSeats().get(id);
    require("active".equals(seat.getStatus()), "PLAYER_OUT", "你已离场，等待下一局");
    require(String.valueOf(poker.getTurnNumber()).equals(action.value("turnNumber")),
        "STALE_TURN", "行动顺序已变化，请按最新画面操作");
    if ("LOOK".equals(action.action())) {
      require(!seat.isSeen(), "ALREADY_ACTED", "你已经看过牌了");
      seat.setSeen(true);
      record(poker, id, "LOOK", 0, "", "");
      return new GameActionResult(false);
    }
    require(id.equals(poker.getCurrentPlayerId()), "NOT_YOUR_TURN", "还没轮到你行动");
    int cost = poker.getBaseStake() * (seat.isSeen() ? 2 : 1);
    switch (action.action()) {
      case "CALL", "RAISE" -> {
        require(poker.getTurnNumber() <= poker.getActionLimit(), "COMPARE_ONLY", "已进入决胜阶段，请比牌或弃牌");
        if ("RAISE".equals(action.action())) {
          require(poker.getBaseStake() < MAX_STAKE, "STAKE_LIMIT", "本局底分已到 5 分上限");
          poker.setBaseStake(poker.getBaseStake() + 1);
          cost = poker.getBaseStake() * (seat.isSeen() ? 2 : 1);
        }
        contribute(poker, seat, cost);
        record(poker, id, action.action(), cost, "", "");
      }
      case "FOLD" -> {
        seat.setStatus("folded");
        record(poker, id, "FOLD", 0, "", id);
      }
      case "COMPARE" -> {
        String targetId = action.value("targetPlayerId");
        var target = poker.getSeats().get(targetId);
        require(target != null && !id.equals(targetId) && "active".equals(target.getStatus()),
            "INVALID_TARGET", "请选择另一位仍在场的玩家");
        contribute(poker, seat, cost * 2);
        String loser = ZhaJinHuaHand.compare(seat.getCards(), target.getCards(), poker.isSpecial235()) > 0
            ? targetId : id;
        poker.getSeats().get(loser).setStatus("lost");
        record(poker, id, "COMPARE", cost * 2, targetId, loser);
      }
      default -> throw new IllegalStateException("Unexpected action");
    }
    advance(c);
    return new GameActionResult(isFinished(c));
  }

  private void contribute(ZhaJinHuaState poker, ZhaJinHuaState.Seat seat, int cost) {
    seat.setContribution(seat.getContribution() + cost);
    poker.setPot(poker.getPot() + cost);
  }

  private void record(ZhaJinHuaState poker, String id, String action, int points, String target, String loser) {
    poker.getMoves().add(new ZhaJinHuaState.Move(id, action, points, target, loser));
    if (poker.getMoves().size() > 80) poker.getMoves().removeFirst();
  }

  private void advance(GameContext c) {
    var poker = c.state().getZhaJinHua();
    List<String> active = poker.getSeats().entrySet().stream()
        .filter(e -> "active".equals(e.getValue().getStatus())).map(Map.Entry::getKey).toList();
    if (active.size() == 1) {
      String winner = active.getFirst();
      poker.setWinnerId(winner);
      poker.getSeats().get(winner).setStatus("winner");
      c.state().setComplete(true);
      event(c, GameEvent.Type.RoundWinnerEvent, List.of(winner));
      event(c, GameEvent.Type.RoundLoserEvent,
          c.state().getParticipants().stream().filter(id -> !id.equals(winner)).toList());
      return;
    }
    var ids = c.state().getParticipants();
    int current = ids.indexOf(poker.getCurrentPlayerId());
    for (int i = 1; i <= ids.size(); i++) {
      String next = ids.get((current + i) % ids.size());
      if (active.contains(next)) {
        poker.setCurrentPlayerId(next);
        break;
      }
    }
    poker.setTurnNumber(poker.getTurnNumber() + 1);
    c.state().setDeadline(System.currentTimeMillis() + TURN_MILLIS);
  }

  @Override
  public void timeout(GameContext c) {
    if (c.state().isComplete()) return;
    var poker = c.state().getZhaJinHua();
    String id = poker.getCurrentPlayerId();
    poker.getSeats().get(id).setStatus("folded");
    record(poker, id, "TIMEOUT", 0, "", id);
    advance(c);
  }

  @Override
  public Map<String, Object> getPublicView(GameContext c) {
    Map<String, Object> view = super.getPublicView(c);
    var poker = c.state().getZhaJinHua();
    Map<String, Object> table = new LinkedHashMap<>();
    table.put("currentPlayerId", poker.getCurrentPlayerId());
    table.put("turnNumber", poker.getTurnNumber());
    table.put("actionLimit", poker.getActionLimit());
    table.put("compareOnly", poker.getTurnNumber() > poker.getActionLimit());
    table.put("baseStake", poker.getBaseStake());
    table.put("maxStake", MAX_STAKE);
    table.put("pot", poker.getPot());
    table.put("special235", poker.isSpecial235());
    table.put("drinkMode", poker.isDrinkMode());
    table.put("moves", List.copyOf(poker.getMoves()));
    table.put("seats", poker.getSeats().entrySet().stream().map(entry -> {
      var seat = entry.getValue();
      Map<String, Object> publicSeat = new LinkedHashMap<>();
      publicSeat.put("playerId", entry.getKey());
      publicSeat.put("seen", seat.isSeen());
      publicSeat.put("status", seat.getStatus());
      publicSeat.put("contribution", seat.getContribution());
      if (c.state().isComplete() && !"folded".equals(seat.getStatus())) {
        publicSeat.put("cards", ZhaJinHuaHand.display(seat.getCards()));
        publicSeat.put("handType", ZhaJinHuaHand.evaluate(seat.getCards()).label());
      }
      if (c.state().isComplete()) publicSeat.put("netPoints",
          (entry.getKey().equals(poker.getWinnerId()) ? poker.getPot() : 0) - seat.getContribution());
      return publicSeat;
    }).toList());
    if (c.state().isComplete()) table.put("winnerId", poker.getWinnerId());
    view.put("poker", table);
    view.put("submittedCount", poker.getSeats().values().stream()
        .filter(seat -> !"active".equals(seat.getStatus())).count());
    return view;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> getPlayerView(GameContext c, Player player) {
    Map<String, Object> view = getPublicView(c);
    var seat = c.state().getZhaJinHua().getSeats().get(player.getPlayerId());
    view.put("hasActed", seat == null || !"active".equals(seat.getStatus()));
    if (seat != null && (seat.isSeen() || c.state().isComplete())) {
      var table = (Map<String, Object>) view.get("poker");
      table.put("myCards", ZhaJinHuaHand.display(seat.getCards()));
      table.put("myHandType", ZhaJinHuaHand.evaluate(seat.getCards()).label());
    }
    return view;
  }
}
