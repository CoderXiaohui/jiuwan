package com.jiuwan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.*;
import com.jiuwan.exception.BusinessException;
import com.jiuwan.game.*;
import com.jiuwan.game.impl.AngryBirdsGameEngine;
import java.util.*;
import org.junit.jupiter.api.Test;

class AngryBirdsTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final RandomSource random = mock(RandomSource.class);
  private final AngryBirdsGameEngine engine = new AngryBirdsGameEngine(random);

  private GameContext start(int players, int bombs) {
    when(random.nextInt(anyInt())).thenAnswer(call -> (int) call.getArgument(0) - 1);
    Room room = new Room();
    room.setOwnerId("p0");
    room.getSettings().setAngryBirdsBombCount(bombs);
    for (int i = 0; i < players; i++) {
      Player player = new Player();
      player.setPlayerId("p" + i);
      player.setConnected(true);
      room.getPlayers().put(player.getPlayerId(), player);
    }
    var c = new GameContext(room);
    engine.start(c);
    return c;
  }

  private Player current(GameContext c) {
    int index = (c.state().getAngryBirds().getTurnNumber() - 1) % c.state().getParticipants().size();
    return c.room().getPlayers().get(c.state().getParticipants().get(index));
  }

  private void pick(GameContext c, int bird) {
    engine.handleAction(c, current(c), new GameAction("PICK_BIRD",
        Map.of("birdId", bird, "turnNumber", c.state().getAngryBirds().getTurnNumber())));
  }

  private void expire(GameContext c) { c.state().setDeadline(System.currentTimeMillis() - 1); }

  @Test
  void playerAndBombLimitsAndTenSecondDeadline() {
    assertEquals(1, new RoomSettings().getAngryBirdsBombCount());
    for (int players : List.of(2, 10)) for (int bombs = 1; bombs <= 6; bombs++) {
      var c = start(players, bombs);
      assertEquals(players, c.state().getParticipants().size());
      assertEquals(bombs, c.state().getAngryBirds().getBombIds().size());
      assertTrue(c.state().getAngryBirds().getBombIds().stream().allMatch(i -> i >= 0 && i < 16));
      assertEquals(c.state().getStartsAt() + 10000, c.state().getDeadline());
    }
    assertEquals("NEED_PLAYERS", assertThrows(BusinessException.class, () -> start(1, 1)).getCode());
    assertEquals("TOO_MANY_PLAYERS", assertThrows(BusinessException.class, () -> start(11, 1)).getCode());
    for (int bombs : List.of(-1, 0, 7))
      assertEquals("INVALID_SETTINGS", assertThrows(BusinessException.class, () -> start(2, bombs)).getCode());
  }

  @Test
  void randomOrderCyclesAndEverySafePositionStaysEmptyUntilExplosion() {
    var c = start(3, 1);
    when(random.nextInt(anyInt())).thenReturn(0);
    engine.start(c);
    assertEquals(List.of("p1", "p2", "p0"), c.state().getParticipants());
    c.state().setStartsAt(0);
    int bomb = c.state().getAngryBirds().getBombIds().iterator().next();
    int moves = 0;
    for (int bird = 0; bird < 16; bird++) if (bird != bomb) {
      String player = c.state().getParticipants().get(moves % 3);
      assertEquals(player, current(c).getPlayerId());
      long before = System.currentTimeMillis();
      pick(c, bird);
      assertEquals(player, c.state().getAngryBirds().getLastMove().playerId());
      assertTrue(c.state().getDeadline() >= before + 10000);
      assertEquals(++moves, c.state().getAngryBirds().getFlownIds().size());
      assertFalse(c.state().isComplete());
    }
    pick(c, bomb);
    assertTrue(c.state().isComplete());
    assertEquals(0, c.state().getDeadline());
    assertEquals("p1", c.state().getAngryBirds().getLoserId());
  }

  @Test
  void invalidActionsAndStaleTurnsCannotChangeBoard() throws Exception {
    var c = start(2, 1);
    assertEquals("COUNTDOWN", assertThrows(BusinessException.class, () -> pick(c, 3)).getCode());
    c.state().setStartsAt(0);
    String before = mapper.writeValueAsString(c.state());
    Player stranger = new Player();
    stranger.setPlayerId("stranger");
    var action = new GameAction("PICK_BIRD", Map.of("birdId", 3, "turnNumber", 1));
    assertEquals("NOT_PARTICIPANT", assertThrows(BusinessException.class,
        () -> engine.handleAction(c, stranger, action)).getCode());
    assertEquals("NOT_YOUR_TURN", assertThrows(BusinessException.class,
        () -> engine.handleAction(c, c.room().getPlayers().get("p1"), action)).getCode());
    assertEquals("INVALID_ACTION", assertThrows(BusinessException.class,
        () -> engine.handleAction(c, current(c), new GameAction("FAKE", Map.of()))).getCode());
    for (Object invalid : List.of(-1, 16, 1.5, true, "", "999999999999999999"))
      assertEquals("INVALID_BIRD", assertThrows(BusinessException.class, () -> engine.handleAction(c,
          current(c), new GameAction("PICK_BIRD", Map.of("birdId", invalid, "turnNumber", 1)))).getCode());
    assertEquals(before, mapper.writeValueAsString(c.state()));
    pick(c, 3);
    assertEquals("STALE_TURN", assertThrows(BusinessException.class,
        () -> engine.handleAction(c, current(c), action)).getCode());
    assertEquals("BIRD_GONE", assertThrows(BusinessException.class, () -> pick(c, 3)).getCode());
    expire(c);
    assertEquals("ROUND_EXPIRED", assertThrows(BusinessException.class, () -> pick(c, 4)).getCode());
    assertEquals(Set.of(3), c.state().getAngryBirds().getFlownIds());
  }

  @Test
  void hiddenViewsNeverExposeBombsThenRevealAllWithoutScoresOrChallenges() throws Exception {
    var c = start(3, 6);
    c.state().setStartsAt(0);
    pick(c, 15);
    for (Player p : c.players()) {
      var json = mapper.valueToTree(engine.getPlayerView(c, p));
      assertFalse(json.has("myChoice"));
      assertFalse(json.toString().contains("bombIds"));
      var birds = json.at("/angryBirds/birds");
      assertEquals(16, birds.size());
      for (int i = 0; i < 15; i++) assertEquals("hidden", birds.get(i).get("status").asText());
      assertEquals("flown", birds.get(15).get("status").asText());
      assertFalse(json.get("angryBirds").has("loserId"));
    }
    Player spectator = new Player();
    spectator.setPlayerId("observer");
    assertEquals(engine.getPublicView(c).get("angryBirds"), engine.getPlayerView(c, spectator).get("angryBirds"));
    pick(c, 2);
    var revealed = mapper.valueToTree(engine.getPublicView(c)).at("/angryBirds/birds");
    for (int i = 0; i < 6; i++) assertEquals(i == 2 ? "exploded" : "bomb", revealed.get(i).get("status").asText());
    assertEquals("p1", c.state().getAngryBirds().getLoserId());
    assertTrue(c.state().getEvents().isEmpty());
    assertTrue(c.players().stream().allMatch(p -> p.getScore() == 0));
    String finished = mapper.writeValueAsString(c.state());
    engine.timeout(c);
    assertEquals("ROUND_FINISHED", assertThrows(BusinessException.class, () -> pick(c, 4)).getCode());
    assertEquals(finished, mapper.writeValueAsString(c.state()));
  }

  @Test
  void timeoutPicksOnlyRemainingBirdsForOfflinePlayerAndCanExplode() throws Exception {
    var c = start(2, 1);
    c.state().setStartsAt(0);
    String before = mapper.writeValueAsString(c.state());
    engine.timeout(c);
    assertEquals(before, mapper.writeValueAsString(c.state()));
    current(c).setConnected(false);
    expire(c);
    engine.timeout(c); // Highest remaining position: 15.
    var board = c.state().getAngryBirds();
    assertEquals(Set.of(15), board.getFlownIds());
    assertTrue(board.getLastMove().automatic());
    assertEquals("p0", board.getLastMove().playerId());
    engine.timeout(c); // Duplicate/early tick cannot consume the next turn.
    assertEquals(2, board.getTurnNumber());
    expire(c);
    engine.timeout(c);
    assertEquals(Set.of(14, 15), board.getFlownIds());
    when(random.nextInt(anyInt())).thenReturn(0);
    expire(c);
    engine.timeout(c);
    assertTrue(c.state().isComplete());
    assertEquals(0, board.getLastMove().birdId());
    assertEquals("p0", board.getLoserId());
    assertTrue(board.getLastMove().automatic());
  }

  @Test
  void snapshotRecoveryAndNewRoundPreserveSettingsButRefreshBoardAndOrder() throws Exception {
    var c = start(3, 4);
    c.state().setStartsAt(0);
    pick(c, 15);
    var restored = new GameContext(mapper.readValue(mapper.writeValueAsString(c.room()), Room.class));
    assertEquals(c.state().getAngryBirds(), restored.state().getAngryBirds());
    assertEquals(engine.getPublicView(c), engine.getPublicView(restored));
    pick(restored, 0);
    String oldInstance = restored.state().getInstanceId();
    restored.room().getPlayers().get("p1").setConnected(false);
    when(random.nextInt(anyInt())).thenReturn(0);
    engine.nextRound(restored);
    assertEquals(List.of("p2", "p0"), restored.state().getParticipants());
    assertEquals(4, restored.state().getAngryBirds().getBombCount());
    assertNotEquals(c.state().getAngryBirds().getBombIds(), restored.state().getAngryBirds().getBombIds());
    assertNotEquals(oldInstance, restored.state().getInstanceId());
    assertEquals(2, restored.round());
    assertFalse(restored.state().isComplete());
    assertTrue(restored.state().getAngryBirds().getFlownIds().isEmpty());
    assertNull(restored.state().getAngryBirds().getLastMove());
    assertNull(restored.state().getAngryBirds().getLoserId());
    assertEquals(1, restored.state().getAngryBirds().getTurnNumber());
    assertTrue(restored.state().getStartsAt() <= System.currentTimeMillis());
  }
}
