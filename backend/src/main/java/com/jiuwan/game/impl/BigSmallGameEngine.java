package com.jiuwan.game.impl;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.Player;
import com.jiuwan.game.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BigSmallGameEngine extends AbstractGameEngine {
  private static final List<String> SUITS = List.of("spades", "hearts", "clubs", "diamonds");

  public BigSmallGameEngine(RandomSource random) {
    super(random);
  }

  public String gameId() {
    return "big-small";
  }

  public String gameName() {
    return "大的喝小的喝";
  }

  protected void initialize(GameContext c) {
    var ids = c.state().getParticipants();
    require(ids.size() <= 20, "TOO_MANY_PLAYERS", "大的喝小的喝最多支持 20 位在线玩家");
    List<Integer> deck = new ArrayList<>();
    for (int card = 0; card < 52; card++) deck.add(card);
    for (int i = deck.size() - 1; i > 0; i--) Collections.swap(deck, i, random.nextInt(i + 1));
    var dealt = new BigSmallState();
    for (int i = 0; i < ids.size(); i++) dealt.getCards().put(ids.get(i), deck.get(i));
    c.state().setBigSmall(dealt);
    c.state().setDeadline(0);
  }

  public GameActionResult handleAction(GameContext c, Player player, GameAction action) {
    require("END_ROUND".equals(action.action()), "INVALID_ACTION", "不支持的游戏操作");
    require(c.room().getOwnerId().equals(player.getPlayerId()), "OWNER_ONLY", "只有房主可以结束本轮");
    // The current owner may have reconnected after dealing and need not hold a card.
    validateRound(c);
    c.state().setComplete(true);
    return new GameActionResult(true);
  }

  @Override
  public void timeout(GameContext c) {
    // This game reveals cards only on the owner's explicit END_ROUND action.
  }

  @Override
  public Map<String, Object> getPublicView(GameContext c) {
    return view(c, null);
  }

  @Override
  public Map<String, Object> getPlayerView(GameContext c, Player player) {
    Map<String, Object> view = view(c, player.getPlayerId());
    view.put("hasActed", false);
    return view;
  }

  private Map<String, Object> view(GameContext c, String viewerId) {
    Map<String, Object> view = super.getPublicView(c);
    boolean participant = viewerId != null && c.state().getParticipants().contains(viewerId);
    List<Map<String, Object>> seats = c.state().getParticipants().stream().map(id -> {
      Map<String, Object> seat = new LinkedHashMap<>();
      seat.put("playerId", id);
      if (c.state().isComplete() || (participant && !id.equals(viewerId))) {
        int card = c.state().getBigSmall().getCards().get(id);
        seat.put("card", Map.of("rank", card % 13 + 2, "suit", SUITS.get(card / 13)));
      }
      return seat;
    }).toList();
    view.put("bigSmall", Map.of("seats", seats));
    return view;
  }
}
