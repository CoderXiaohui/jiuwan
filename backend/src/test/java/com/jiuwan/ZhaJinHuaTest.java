package com.jiuwan;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.*;
import com.jiuwan.exception.BusinessException;
import com.jiuwan.game.*;
import com.jiuwan.game.impl.*;
import com.jiuwan.service.GameEventPresenter;
import java.util.*;
import org.junit.jupiter.api.Test;

class ZhaJinHuaTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final ZhaJinHuaGameEngine engine = new ZhaJinHuaGameEngine(new RandomSource());

  private GameContext start(int count) {
    Room room = new Room();
    room.setCurrentGameId(engine.gameId());
    room.setOwnerId("p0");
    for (int i = 0; i < count; i++) {
      Player player = new Player();
      player.setPlayerId("p" + i);
      player.setNickname("玩家" + i);
      player.setConnected(true);
      room.getPlayers().put(player.getPlayerId(), player);
    }
    GameContext c = new GameContext(room);
    engine.start(c);
    c.state().setStartsAt(0);
    return c;
  }

  private void act(GameContext c, String id, String action, String target) {
    engine.handleAction(c, c.room().getPlayers().get(id),
        new GameAction(action, Map.of("turnNumber", c.state().getZhaJinHua().getTurnNumber(),
            "targetPlayerId", target)));
  }

  private void act(GameContext c, String id, String action) { act(c, id, action, ""); }

  @SuppressWarnings("unchecked")
  private Map<String, Object> table(GameContext c, String id) {
    return (Map<String, Object>) engine.getPlayerView(c, c.room().getPlayers().get(id)).get("poker");
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> seats(GameContext c, String id) {
    return (List<Map<String, Object>>) table(c, id).get("seats");
  }

  private static List<Integer> hand(int... suitRankPairs) {
    List<Integer> cards = new ArrayList<>();
    for (int i = 0; i < suitRankPairs.length; i += 2)
      cards.add(suitRankPairs[i] * 13 + suitRankPairs[i + 1] - 2);
    return cards;
  }

  @Test
  void all22100HandsHaveExpectedCategoryCounts() {
    int[] counts = new int[6];
    for (int a = 0; a < 50; a++)
      for (int b = a + 1; b < 51; b++)
        for (int c = b + 1; c < 52; c++) counts[ZhaJinHuaHand.evaluate(List.of(a, b, c)).category()]++;
    assertArrayEquals(new int[] {16440, 3744, 720, 1096, 48, 52}, counts);
  }

  @Test
  void handOrderWheelKickersAndSuitsFollowPublishedTableRules() {
    List<List<Integer>> ascending = List.of(
        hand(0, 14, 1, 13, 2, 9), hand(0, 2, 1, 2, 2, 3),
        hand(0, 14, 1, 2, 2, 3), hand(0, 2, 0, 3, 0, 5),
        hand(0, 14, 0, 2, 0, 3), hand(0, 2, 1, 2, 2, 2));
    for (int i = 1; i < ascending.size(); i++)
      assertTrue(ZhaJinHuaHand.compare(ascending.get(i), ascending.get(i - 1), false) > 0);
    assertTrue(ZhaJinHuaHand.compare(hand(0, 2, 1, 3, 2, 4), hand(0, 14, 1, 2, 2, 3), false) > 0);
    assertEquals("散牌", ZhaJinHuaHand.evaluate(hand(0, 13, 1, 14, 2, 2)).label());
    assertTrue(ZhaJinHuaHand.compare(hand(0, 12, 1, 13, 2, 14), hand(0, 11, 1, 12, 2, 13), false) > 0);
    assertTrue(ZhaJinHuaHand.compare(hand(0, 8, 1, 8, 2, 14), hand(2, 8, 3, 8, 0, 13), false) > 0);
    assertTrue(ZhaJinHuaHand.compare(hand(0, 9, 1, 9, 2, 2), hand(2, 8, 3, 8, 0, 14), false) > 0);
    assertTrue(ZhaJinHuaHand.compare(hand(0, 14, 1, 9, 2, 6), hand(1, 14, 2, 9, 3, 5), false) > 0);
    assertEquals(0, ZhaJinHuaHand.compare(hand(0, 14, 1, 9, 2, 6), hand(1, 14, 2, 9, 3, 6), false));
  }

  @Test
  void special235IsOptionalAndOnlyBeatsTrips() {
    var special = hand(0, 2, 0, 3, 1, 5);
    var trips = hand(0, 14, 1, 14, 2, 14);
    var highCard = hand(0, 6, 1, 3, 2, 2);
    assertTrue(ZhaJinHuaHand.compare(special, trips, false) < 0);
    assertTrue(ZhaJinHuaHand.compare(special, trips, true) > 0);
    assertTrue(ZhaJinHuaHand.compare(trips, special, true) < 0);
    assertTrue(ZhaJinHuaHand.compare(special, highCard, true) < 0);
    assertTrue(ZhaJinHuaHand.compare(hand(0, 2, 0, 3, 0, 5), trips, true) < 0);
  }

  @Test
  void dealsWithoutReplacementFor17AndRejects18() {
    var c = start(17);
    var cards = c.state().getZhaJinHua().getSeats().values().stream().flatMap(s -> s.getCards().stream()).toList();
    assertEquals(51, new HashSet<>(cards).size());
    assertTrue(cards.stream().allMatch(card -> card >= 0 && card < 52));
    assertEquals(17, c.state().getZhaJinHua().getPot());
    assertEquals("TOO_MANY_PLAYERS", assertThrows(BusinessException.class, () -> start(18)).getCode());
  }

  @Test
  void lookingOffTurnRevealsOnlyOwnCardsAndDoesNotResetTimer() throws Exception {
    var c = start(3);
    assertFalse(table(c, "p0").containsKey("myCards"));
    long deadline = c.state().getDeadline();
    act(c, "p1", "LOOK");
    assertEquals(deadline, c.state().getDeadline());
    assertEquals("p0", c.state().getZhaJinHua().getCurrentPlayerId());
    assertNotNull(table(c, "p1").get("myCards"));
    assertFalse(table(c, "p0").containsKey("myCards"));
    assertTrue(seats(c, "p0").stream().noneMatch(s -> s.containsKey("cards") || s.containsKey("handType")));
    assertFalse(mapper.writeValueAsString(engine.getPublicView(c)).contains("myCards"));
    assertFalse(mapper.writeValueAsString(engine.getPlayerView(c, c.players().getFirst())).contains("zhaJinHua"));
    assertEquals("ALREADY_ACTED", assertThrows(BusinessException.class, () -> act(c, "p1", "LOOK")).getCode());
  }

  @Test
  void turnTargetCountdownAndStaleActionsCannotChangeState() throws Exception {
    var c = start(3);
    String initial = mapper.writeValueAsString(c.state());
    assertEquals("NOT_YOUR_TURN", assertThrows(BusinessException.class, () -> act(c, "p1", "CALL")).getCode());
    for (String target : List.of("p0", "missing"))
      assertEquals("INVALID_TARGET", assertThrows(BusinessException.class, () -> act(c, "p0", "COMPARE", target)).getCode());
    assertEquals("INVALID_ACTION", assertThrows(BusinessException.class, () -> act(c, "p0", "FAKE")).getCode());
    assertEquals(initial, mapper.writeValueAsString(c.state()));
    c.state().setStartsAt(System.currentTimeMillis() + 10000);
    assertEquals("COUNTDOWN", assertThrows(BusinessException.class, () -> act(c, "p0", "LOOK")).getCode());
    c.state().setStartsAt(0);
    assertEquals("STALE_TURN", assertThrows(BusinessException.class,
        () -> engine.handleAction(c, c.players().getFirst(), new GameAction("CALL", Map.of("turnNumber", 0)))).getCode());
    c.state().setDeadline(System.currentTimeMillis() - 1);
    assertEquals("ROUND_EXPIRED", assertThrows(BusinessException.class, () -> act(c, "p0", "CALL")).getCode());
  }

  @Test
  void blindSeenRaiseAndCompareCostsAreServerCalculated() {
    var c = start(3);
    act(c, "p0", "CALL"); // +1
    act(c, "p1", "LOOK");
    act(c, "p1", "RAISE"); // new base 2, seen +4
    act(c, "p2", "CALL"); // blind +2
    act(c, "p0", "LOOK");
    act(c, "p0", "COMPARE", "p1"); // seen 2 * 2 * 2 = 8
    var poker = c.state().getZhaJinHua();
    assertEquals(18, poker.getPot());
    assertEquals(10, poker.getSeats().get("p0").getContribution());
    assertEquals(5, poker.getSeats().get("p1").getContribution());
    assertEquals(3, poker.getSeats().get("p2").getContribution());
    assertFalse(c.state().isComplete());
  }

  @Test
  void equalCardsLoseForInitiatorAndEliminatedSeatIsSkipped() {
    var c = start(3);
    c.state().getZhaJinHua().getSeats().get("p0").setCards(hand(0, 14, 1, 9, 2, 6));
    c.state().getZhaJinHua().getSeats().get("p1").setCards(hand(1, 14, 2, 9, 3, 6));
    act(c, "p0", "COMPARE", "p1");
    assertEquals("lost", c.state().getZhaJinHua().getSeats().get("p0").getStatus());
    assertEquals("p1", c.state().getZhaJinHua().getCurrentPlayerId());
    assertEquals("PLAYER_OUT", assertThrows(BusinessException.class, () -> act(c, "p0", "LOOK")).getCode());
    act(c, "p1", "CALL");
    act(c, "p2", "CALL");
    assertEquals("p1", c.state().getZhaJinHua().getCurrentPlayerId());
    assertEquals("INVALID_TARGET", assertThrows(BusinessException.class, () -> act(c, "p1", "COMPARE", "p0")).getCode());
  }

  @Test
  void special235UsesPairwiseEliminationEvenWithThreeWayCycle() {
    var c = start(3);
    var poker = c.state().getZhaJinHua();
    poker.setSpecial235(true);
    poker.getSeats().get("p0").setCards(hand(0, 2, 1, 3, 2, 5));
    poker.getSeats().get("p1").setCards(hand(0, 14, 1, 14, 2, 14));
    poker.getSeats().get("p2").setCards(hand(0, 6, 1, 8, 2, 10));
    act(c, "p0", "COMPARE", "p1");
    assertEquals("p2", poker.getCurrentPlayerId());
    act(c, "p2", "COMPARE", "p0");
    assertEquals("p2", poker.getWinnerId());
  }

  @Test
  void foldsStayPrivateAtSettlementAndPointsSumToZero() {
    var c = start(3);
    act(c, "p0", "LOOK");
    act(c, "p0", "FOLD");
    act(c, "p1", "COMPARE", "p2");
    assertTrue(c.state().isComplete());
    assertFalse(seats(c, "p2").getFirst().containsKey("cards"));
    assertTrue(seats(c, "p2").subList(1, 3).stream().allMatch(s -> s.containsKey("cards")));
    assertEquals(0, seats(c, "p0").stream().mapToInt(s -> (Integer) s.get("netPoints")).sum());
    assertEquals(2, c.state().getEvents().get(1).playerIds().size());
    var events = new GameEventPresenter().present(c.room());
    assertTrue(events.get(1).get("message").toString().contains("每人喝 1 小口"));
    assertFalse(events.getFirst().get("message").toString().contains("默契"));
  }

  @Test
  void timeoutFoldsOnlyCurrentPlayerAndCompletesOnce() {
    var c = start(3);
    engine.timeout(c);
    assertFalse(c.state().isComplete());
    assertEquals("p1", c.state().getZhaJinHua().getCurrentPlayerId());
    assertEquals("TIMEOUT", c.state().getZhaJinHua().getMoves().getFirst().action());
    assertTrue(c.state().getDeadline() > System.currentTimeMillis());
    engine.timeout(c);
    assertTrue(c.state().isComplete());
    assertEquals("p2", c.state().getZhaJinHua().getWinnerId());
    engine.timeout(c);
    assertEquals(2, c.state().getEvents().size());
  }

  @Test
  void stakeCapAndActionCapForceFiniteGame() {
    var c = start(2);
    for (int i = 0; i < 4; i++) act(c, "p" + (i % 2), "RAISE");
    assertEquals(5, c.state().getZhaJinHua().getBaseStake());
    assertEquals("STAKE_LIMIT", assertThrows(BusinessException.class, () -> act(c, "p0", "RAISE")).getCode());
    for (int i = 4; i < 10; i++) act(c, "p" + (i % 2), "CALL");
    assertEquals(true, table(c, "p0").get("compareOnly"));
    assertEquals("COMPARE_ONLY", assertThrows(BusinessException.class, () -> act(c, "p0", "CALL")).getCode());
    act(c, "p0", "COMPARE", "p1");
    assertTrue(c.state().isComplete());
  }

  @Test
  void serverRestartRoundTripKeepsCardsSettingsAndTurn() throws Exception {
    var c = start(3);
    act(c, "p0", "LOOK");
    act(c, "p0", "RAISE");
    var savedView = table(c, "p0");
    var restored = new GameContext(mapper.readValue(mapper.writeValueAsString(c.room()), Room.class));
    assertEquals(savedView, table(restored, "p0"));
    assertFalse(table(restored, "p1").containsKey("myCards"));
    act(restored, "p1", "FOLD");
    assertEquals("p2", restored.state().getZhaJinHua().getCurrentPlayerId());
  }

  @Test
  void nextRoundRotatesFirstPlayerResetsPointsAndSnapshotsSettings() {
    var c = start(2);
    String oldInstance = c.state().getInstanceId();
    act(c, "p0", "FOLD");
    c.room().getSettings().setZhaJinHua235(true);
    c.room().getSettings().setZhaJinHuaDrink(false);
    c.room().getSettings().setPunishmentMode("truth");
    engine.nextRound(c);
    assertNotEquals(oldInstance, c.state().getInstanceId());
    assertEquals(2, c.state().getRound());
    assertEquals(2, c.state().getZhaJinHua().getPot());
    assertEquals("p1", c.state().getZhaJinHua().getCurrentPlayerId());
    assertTrue(c.state().getZhaJinHua().isSpecial235());
    assertFalse(c.state().getZhaJinHua().isDrinkMode());
    assertTrue(c.state().getZhaJinHua().getMoves().isEmpty());
    assertFalse(table(c, "p0").containsKey("myCards"));
    c.state().setStartsAt(0);
    act(c, "p1", "FOLD");
    assertTrue(new GameEventPresenter().present(c.room()).get(1).get("message").toString().contains("真心话"));
  }
}
