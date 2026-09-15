package com.jiuwan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.*;
import com.jiuwan.exception.BusinessException;
import com.jiuwan.game.*;
import com.jiuwan.game.impl.BigSmallGameEngine;
import java.util.*;
import org.junit.jupiter.api.Test;

class BigSmallTest {
  private final BigSmallGameEngine engine = new BigSmallGameEngine(new RandomSource());
  private final ObjectMapper mapper = new ObjectMapper();

  private GameContext start(int count) {
    Room room = new Room();
    room.setOwnerId("p0");
    for (int i = 0; i < count; i++) {
      Player player = new Player();
      player.setPlayerId("p" + i);
      player.setConnected(true);
      room.getPlayers().put(player.getPlayerId(), player);
    }
    var c = new GameContext(room);
    engine.start(c);
    c.state().setStartsAt(0);
    return c;
  }

  private void act(GameContext c, String player, String action) {
    engine.handleAction(c, c.room().getPlayers().get(player), new GameAction(action, Map.of()));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> seats(Map<String, Object> view) {
    return (List<Map<String, Object>>) ((Map<String, Object>) view.get("bigSmall")).get("seats");
  }

  private Map<String, Object> view(GameContext c, String id) {
    return engine.getPlayerView(c, c.room().getPlayers().get(id));
  }

  @Test
  void dealsOneUniqueNonJokerCardPerPlayerAtBothRoomLimits() {
    for (int count : List.of(2, 20)) {
      var c = start(count);
      var cards = c.state().getBigSmall().getCards();
      assertEquals(c.state().getParticipants(), new ArrayList<>(cards.keySet()));
      assertEquals(count, cards.size());
      assertEquals(count, new HashSet<>(cards.values()).size());
      assertTrue(cards.values().stream().allMatch(card -> card >= 0 && card < 52));
      assertEquals(0, c.state().getDeadline());
    }
    assertEquals("NEED_PLAYERS", assertThrows(BusinessException.class, () -> start(1)).getCode());
    assertEquals("TOO_MANY_PLAYERS", assertThrows(BusinessException.class, () -> start(21)).getCode());
  }

  @Test
  void onlyOtherParticipantsCardsAreSentAndPublicAndSpectatorViewsStayHidden() throws Exception {
    var c = start(3);
    // Known cards make leaks and suit/rank conversion observable in serialized snapshots.
    c.state().getBigSmall().setCards(new LinkedHashMap<>(Map.of("p0", 12, "p1", 13, "p2", 51)));
    var expected = List.of(
        Map.of("rank", 14, "suit", "spades"),
        Map.of("rank", 2, "suit", "hearts"),
        Map.of("rank", 14, "suit", "diamonds"));
    for (int viewer = 0; viewer < 3; viewer++) {
      var personal = view(c, "p" + viewer);
      var seats = seats(personal);
      assertEquals(List.of("p0", "p1", "p2"), seats.stream().map(s -> s.get("playerId")).toList());
      for (int seat = 0; seat < 3; seat++) {
        if (seat == viewer) assertEquals(Map.of("playerId", "p" + seat), seats.get(seat));
        else assertEquals(expected.get(seat), seats.get(seat).get("card"));
      }
      var json = mapper.readTree(mapper.writeValueAsString(personal));
      assertFalse(json.has("myChoice"));
      assertFalse(json.has("privateChoices"));
      assertFalse(json.get("bigSmall").has("cards"));
    }
    assertTrue(seats(engine.getPublicView(c)).stream().noneMatch(s -> s.containsKey("card")));
    Player spectator = new Player();
    spectator.setPlayerId("spectator");
    assertTrue(seats(engine.getPlayerView(c, spectator)).stream().noneMatch(s -> s.containsKey("card")));

    act(c, "p0", "END_ROUND");
    var revealed = seats(engine.getPublicView(c));
    assertEquals(expected, revealed.stream().map(s -> s.get("card")).toList());
    for (String id : c.state().getParticipants()) assertEquals(revealed, seats(view(c, id)));
    assertEquals(revealed, seats(engine.getPlayerView(c, spectator)));
  }

  @Test
  void onlyOwnerCanEndAfterCountdownAndNoRulesOrScoresAreCalculated() throws Exception {
    var c = start(3);
    String before = mapper.writeValueAsString(c.state());
    assertEquals("OWNER_ONLY", assertThrows(BusinessException.class,
        () -> act(c, "p1", "END_ROUND")).getCode());
    for (String action : List.of("LOOK", "DRAW", "SHAKE_DICE", "FAKE"))
      assertEquals("INVALID_ACTION", assertThrows(BusinessException.class,
          () -> act(c, "p0", action)).getCode());
    assertEquals(before, mapper.writeValueAsString(c.state()));
    c.state().setStartsAt(System.currentTimeMillis() + 10000);
    assertEquals("COUNTDOWN", assertThrows(BusinessException.class,
        () -> act(c, "p0", "END_ROUND")).getCode());
    c.state().setStartsAt(0);
    act(c, "p0", "END_ROUND");
    assertTrue(c.state().isComplete());
    assertTrue(c.state().getEvents().isEmpty());
    assertTrue(c.state().getPublicData().isEmpty());
    assertTrue(c.state().getPrivateChoices().isEmpty());
    assertTrue(c.players().stream().allMatch(p -> p.getScore() == 0));
    assertEquals("ROUND_FINISHED", assertThrows(BusinessException.class,
        () -> act(c, "p0", "END_ROUND")).getCode());
  }

  @Test
  void timeoutCannotRevealAndJsonRecoveryPreservesTheSamePrivateDeal() throws Exception {
    var c = start(3);
    var initial = view(c, "p0");
    engine.timeout(c);
    assertEquals(initial, view(c, "p0"));
    var restored = new GameContext(mapper.readValue(mapper.writeValueAsString(c.room()), Room.class));
    assertEquals(c.state().getBigSmall(), restored.state().getBigSmall());
    for (String id : c.state().getParticipants()) assertEquals(view(c, id), view(restored, id));
  }

  @Test
  void eachRoundShufflesAFullDeckAndUsesFreshOnlineParticipantsAndHiddenViews() {
    var c = start(4);
    RandomSource random = mock(RandomSource.class);
    when(random.nextInt(anyInt())).thenAnswer(call -> (int) call.getArgument(0) - 1);
    var fixedEngine = new BigSmallGameEngine(random);
    fixedEngine.start(c);
    var first = c.state();
    c.state().setStartsAt(0);
    act(c, "p0", "END_ROUND");
    c.room().getPlayers().get("p3").setConnected(false);
    fixedEngine.nextRound(c);
    assertNotSame(first, c.state());
    assertNotEquals(first.getInstanceId(), c.state().getInstanceId());
    assertEquals(2, c.round());
    assertEquals(0, c.state().getDeadline());
    assertTrue(c.state().getStartsAt() <= System.currentTimeMillis());
    assertFalse(c.state().isComplete());
    assertEquals(List.of("p0", "p1", "p2"), c.state().getParticipants());
    assertEquals(Map.of("p0", 0, "p1", 1, "p2", 2), c.state().getBigSmall().getCards());
    assertFalse(seats(view(c, "p0")).getFirst().containsKey("card"));
    assertTrue(seats(view(c, "p3")).stream().noneMatch(s -> s.containsKey("card")));
    for (int bound = 2; bound <= 52; bound++) verify(random, times(2)).nextInt(bound);
  }
}
