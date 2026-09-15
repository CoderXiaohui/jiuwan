package com.jiuwan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.*;
import com.jiuwan.dto.Requests;
import com.jiuwan.dto.RoomView;
import com.jiuwan.exception.BusinessException;
import com.jiuwan.game.*;
import com.jiuwan.game.impl.*;
import com.jiuwan.repository.RoomRepository;
import com.jiuwan.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class PlatformTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private RandomSource random;
  private RoomService rooms;
  private IdentityService identity;
  private GameRegistry registry;
  private GameService games;
  private RoomViewService views;
  private RoomView.Credentials owner, guest;
  private QuestionBank bank;

  @BeforeEach
  void setup() throws Exception {
    random = new RandomSource();
    identity = new IdentityService();
    bank = new QuestionBank(mapper);
    registry =
        new GameRegistry(
            List.of(
                new VoteGameEngine(random, bank),
                new DiceGameEngine(random),
                new RouletteGameEngine(random),
                new TruthGameEngine(random, bank),
                new CompatibilityGameEngine(random, bank),
                new ZhaJinHuaGameEngine(random)));
    rooms = new RoomService(new MemoryRepository(), identity, random, event -> {});
    games = new GameService(rooms, registry, mapper, identity);
    views = new RoomViewService(rooms, identity, registry, new GameEventPresenter());
    owner = rooms.create(new Requests.Profile("小辉", "😎"));
    guest = rooms.join(owner.roomCode(), new Requests.Profile("老王", "🐼"));
    rooms.reconnect(owner.roomCode(), owner.playerToken());
    rooms.reconnect(owner.roomCode(), guest.playerToken());
  }

  class MemoryRepository implements RoomRepository {
    final Map<String, String> db = new ConcurrentHashMap<>();

    public Room find(String code) {
      try {
        return db.containsKey(code) ? mapper.readValue(db.get(code), Room.class) : null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    String encode(Room room) {
      try {
        return mapper.writeValueAsString(room);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public boolean create(Room room) {
      return db.putIfAbsent(room.getRoomCode(), encode(room)) == null;
    }

    public void save(Room room) {
      db.put(room.getRoomCode(), encode(room));
    }

    public Set<String> codes() {
      return db.keySet();
    }
  }

  private void start(String id) {
    games.start(owner.roomCode(), owner.playerToken(), id, UUID.randomUUID().toString());
    rooms.mutate(
        owner.roomCode(),
        r -> {
          r.getGameState().setStartsAt(0);
          return null;
        },
        null);
  }

  private Requests.Command command(
      String game, String instance, String action, Map<String, Object> data, String requestId) {
    return new Requests.Command(
        "GAME_ACTION", requestId, owner.roomCode(), null, null, game, instance, action, data);
  }

  private void act(RoomView.Credentials player, String action, Map<String, Object> data) {
    RoomView view = views.get(owner.roomCode(), player.playerToken());
    games.command(
        owner.roomCode(),
        player.playerToken(),
        command(
            view.currentGameId(),
            view.game() == null ? null : (String) view.game().get("instanceId"),
            action,
            data,
            UUID.randomUUID().toString()));
  }

  private Map<String, Object> game(RoomView.Credentials player) {
    return views.get(owner.roomCode(), player.playerToken()).game();
  }

  @Test
  void roomServiceCreatesSixDigitCodeAndDistinctTokens() {
    assertTrue(owner.roomCode().matches("[0-9]{6}"));
    assertNotEquals(owner.playerToken(), guest.playerToken());
    assertEquals(2, views.get(owner.roomCode(), owner.playerToken()).players().size());
  }

  @Test
  void joinRejectsMissingRoomAndInvalidAvatar() {
    assertThrows(
        BusinessException.class, () -> rooms.join("000000", new Requests.Profile("A", "🐼")));
    assertThrows(
        BusinessException.class,
        () -> rooms.join(owner.roomCode(), new Requests.Profile("A", "xxx")));
  }

  @Test
  void fullRoomRejectsJoin() {
    rooms.mutate(
        owner.roomCode(),
        r -> {
          r.getSettings().setMaxPlayers(2);
          return null;
        },
        null);
    assertThrows(
        BusinessException.class,
        () -> rooms.join(owner.roomCode(), new Requests.Profile("第三位", "🐼")));
  }

  @Test
  void ownerTransfersOnLeave() {
    act(owner, "LEAVE_ROOM", Map.of());
    assertEquals(guest.playerId(), views.get(owner.roomCode(), guest.playerToken()).ownerId());
    assertThrows(
        BusinessException.class, () -> rooms.reconnect(owner.roomCode(), owner.playerToken()));
  }

  @Test
  void reconnectPreservesPlayerAndOwnerDuringGracePeriod() {
    rooms.disconnect(owner.roomCode(), owner.playerId());
    assertEquals(owner.playerId(), rooms.reconnect(owner.roomCode(), owner.playerToken()));
    assertEquals(owner.playerId(), views.get(owner.roomCode(), owner.playerToken()).ownerId());
  }

  @Test
  void staleSocketClosureCannotDisconnectReplacement() {
    rooms.disconnect(owner.roomCode(), owner.playerId(), () -> false);
    assertTrue(
        views.get(owner.roomCode(), owner.playerToken()).players().stream()
            .filter(p -> p.playerId().equals(owner.playerId()))
            .findFirst()
            .orElseThrow()
            .connected());
  }

  @Test
  void actionsAfterDeadlineAreRejectedBeforeScheduledSettlement() {
    start("vote");
    rooms.mutate(
        owner.roomCode(),
        r -> {
          r.getGameState().setDeadline(System.currentTimeMillis() - 1);
          return null;
        },
        null);
    BusinessException error =
        assertThrows(
            BusinessException.class,
            () -> act(owner, "VOTE", Map.of("targetPlayerId", guest.playerId())));
    assertEquals("ROUND_EXPIRED", error.getCode());
    assertEquals(0, game(owner).get("submittedCount"));
  }

  @Test
  void nullSettingsAreRejectedAsInputError() {
    Map<String, Object> invalid = new HashMap<>();
    invalid.put("gameMode", null);
    BusinessException error =
        assertThrows(BusinessException.class, () -> act(owner, "UPDATE_SETTINGS", invalid));
    assertEquals("INVALID_SETTINGS", error.getCode());
  }

  @Test
  void offlineOwnerTransfersAfterGracePeriod() {
    rooms.disconnect(owner.roomCode(), owner.playerId());
    rooms.maintain(owner.roomCode(), System.currentTimeMillis() + 16000, games::timeout);
    assertEquals(guest.playerId(), views.get(owner.roomCode(), guest.playerToken()).ownerId());
  }

  @Test
  void idleRoomCloses() {
    rooms.disconnect(owner.roomCode(), owner.playerId());
    rooms.disconnect(owner.roomCode(), guest.playerId());
    rooms.maintain(owner.roomCode(), System.currentTimeMillis() + 1800001, games::timeout);
    assertThrows(BusinessException.class, () -> views.preview(owner.roomCode()));
  }

  @Test
  void registryDiscoversSixPluginsAndRejectsDuplicates() {
    assertEquals(6, registry.list().size());
    assertThrows(BusinessException.class, () -> registry.get("poker"));
    var engine = new DiceGameEngine(random);
    assertThrows(IllegalStateException.class, () -> new GameRegistry(List.of(engine, engine)));
  }

  @Test
  void nonOwnerCannotStartOrChangeSettings() {
    assertThrows(
        BusinessException.class,
        () -> games.start(owner.roomCode(), guest.playerToken(), "vote", "start"));
    assertThrows(BusinessException.class, () -> act(guest, "UPDATE_SETTINGS", Map.of()));
  }

  @Test
  void invalidPlayerCannotReadOrAct() {
    assertThrows(BusinessException.class, () -> views.get(owner.roomCode(), "not-a-real-token"));
    assertThrows(
        BusinessException.class,
        () -> games.start(owner.roomCode(), "x".repeat(43), "vote", "start"));
  }

  @Test
  void viewsNeverContainTokenHashes() throws Exception {
    String json = mapper.writeValueAsString(views.get(owner.roomCode(), owner.playerToken()));
    assertFalse(json.contains("token"));
    assertFalse(json.contains(owner.playerToken()));
  }

  @Test
  void votesArePrivateAndTiesArePreserved() {
    start("vote");
    act(owner, "VOTE", Map.of("targetPlayerId", owner.playerId()));
    assertFalse(game(guest).containsKey("myChoice"));
    assertFalse(game(guest).containsKey("counts"));
    assertFalse(game(guest).containsKey("privateChoices"));
    act(guest, "VOTE", Map.of("targetPlayerId", guest.playerId()));
    assertEquals(true, game(owner).get("complete"));
    assertEquals(2, ((List<?>) game(owner).get("selectedIds")).size());
    assertFalse(game(owner).containsKey("ballots"));
  }

  @Test
  void voteRejectsInvalidTargetWithoutConsumingAction() {
    start("vote");
    assertThrows(
        BusinessException.class, () -> act(owner, "VOTE", Map.of("targetPlayerId", "imposter")));
    assertEquals(false, game(owner).get("hasActed"));
  }

  @Test
  void voteTimeoutRevealsOnlySubmittedVotes() {
    start("vote");
    act(owner, "VOTE", Map.of("targetPlayerId", guest.playerId()));
    rooms.maintain(owner.roomCode(), System.currentTimeMillis() + 70000, games::timeout);
    assertEquals(true, game(owner).get("complete"));
    assertEquals(List.of(guest.playerId()), game(owner).get("selectedIds"));
  }

  @Test
  void duplicateRequestIsIdempotentAndNewDuplicateIsRejected() {
    start("vote");
    String instance = (String) game(owner).get("instanceId");
    var command =
        command("vote", instance, "VOTE", Map.of("targetPlayerId", guest.playerId()), "same-id");
    games.command(owner.roomCode(), owner.playerToken(), command);
    games.command(owner.roomCode(), owner.playerToken(), command);
    assertEquals(1, game(owner).get("submittedCount"));
    assertThrows(
        BusinessException.class,
        () -> act(owner, "VOTE", Map.of("targetPlayerId", owner.playerId())));
  }

  @Test
  void concurrentDuplicateVotesCommitOnce() throws Exception {
    start("vote");
    var command =
        command(
            "vote",
            (String) game(owner).get("instanceId"),
            "VOTE",
            Map.of("targetPlayerId", guest.playerId()),
            "parallel");
    try (var pool = Executors.newFixedThreadPool(8)) {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 16; i++)
        futures.add(
            pool.submit(() -> games.command(owner.roomCode(), owner.playerToken(), command)));
      for (var f : futures) f.get();
    }
    assertEquals(1, game(owner).get("submittedCount"));
  }

  @Test
  void staleRoundActionRejected() {
    start("dice");
    String old = (String) game(owner).get("instanceId");
    act(owner, "SHAKE_DICE", Map.of());
    act(guest, "SHAKE_DICE", Map.of());
    act(owner, "NEXT_ROUND", Map.of());
    assertThrows(
        BusinessException.class,
        () ->
            games.command(
                owner.roomCode(),
                guest.playerToken(),
                command("dice", old, "SHAKE_DICE", Map.of(), "old")));
    assertEquals(0, game(owner).get("submittedCount"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void pokerViewsReconnectAndDuplicateRequestsStayPrivateAndIdempotent() throws Exception {
    start("zhajinhua");
    String instance = (String) game(owner).get("instanceId");
    var call = command("zhajinhua", instance, "CALL", Map.of("turnNumber", 1), "poker-call");
    games.command(owner.roomCode(), owner.playerToken(), call);
    games.command(owner.roomCode(), owner.playerToken(), call);
    assertEquals(3, ((Map<String, Object>) game(owner).get("poker")).get("pot"));
    act(guest, "LOOK", Map.of("turnNumber", 2));
    var guestCards = ((Map<String, Object>) game(guest).get("poker")).get("myCards");
    assertNotNull(guestCards);
    rooms.disconnect(owner.roomCode(), guest.playerId());
    rooms.reconnect(owner.roomCode(), guest.playerToken());
    assertEquals(guestCards, ((Map<String, Object>) game(guest).get("poker")).get("myCards"));
    assertFalse(mapper.writeValueAsString(game(owner)).contains("myCards"));
    assertFalse(mapper.writeValueAsString(game(owner)).contains("zhaJinHua"));
    act(guest, "COMPARE", Map.of("turnNumber", 2, "targetPlayerId", owner.playerId()));
    assertEquals(true, game(owner).get("complete"));
    act(owner, "NEXT_ROUND", Map.of());
    assertEquals("STALE_ACTION", assertThrows(BusinessException.class,
        () -> games.command(owner.roomCode(), owner.playerToken(),
            command("zhajinhua", instance, "CALL", Map.of("turnNumber", 1), "old-poker-call"))).getCode());
  }

  @Test
  void diceGeneratedByServerAndHiddenUntilReveal() {
    start("dice");
    act(owner, "SHAKE_DICE", Map.of("value", 999));
    int roll = Integer.parseInt((String) game(owner).get("myChoice"));
    assertTrue(roll >= 1 && roll <= 6);
    assertFalse(game(guest).containsKey("rolls"));
    act(guest, "SHAKE_DICE", Map.of());
    assertEquals(true, game(owner).get("complete"));
    assertFalse(((List<?>) game(owner).get("loserIds")).isEmpty());
  }

  @Test
  void diceSupportsHighestLosesAndTies() throws Exception {
    RandomSource fixed = mock(RandomSource.class);
    when(fixed.nextInt(6)).thenReturn(5, 5);
    Room room = rooms.read(owner.roomCode(), r -> r);
    room.getSettings().setDiceRule("highest");
    var engine = new DiceGameEngine(fixed);
    var c = new GameContext(room);
    engine.start(c);
    c.state().setStartsAt(0);
    for (Player p : c.players()) engine.handleAction(c, p, new GameAction("SHAKE_DICE", Map.of()));
    assertEquals(2, ((List<?>) engine.getPublicView(c).get("loserIds")).size());
  }

  @Test
  void compatibilityHidesAnswerAndAwardsMatchingPair() {
    start("compatibility");
    String answer = (String) ((List<?>) game(owner).get("options")).get(0);
    act(owner, "ANSWER", Map.of("answer", answer));
    assertFalse(game(guest).containsKey("answers"));
    assertFalse(game(guest).containsKey("myChoice"));
    act(guest, "ANSWER", Map.of("answer", answer));
    assertEquals(true, game(owner).get("matched"));
    assertTrue(
        views.get(owner.roomCode(), owner.playerToken()).players().stream()
            .allMatch(p -> p.score() == 1));
  }

  @Test
  void compatibilityTimeoutDoesNotLeakSingleAnswer() {
    start("compatibility");
    String answer = (String) ((List<?>) game(owner).get("options")).get(0);
    act(owner, "ANSWER", Map.of("answer", answer));
    rooms.maintain(owner.roomCode(), System.currentTimeMillis() + 70000, games::timeout);
    assertEquals(true, game(guest).get("cancelled"));
    assertFalse(game(guest).containsKey("answers"));
  }

  @Test
  void gameSwitchRetainsRoomAndPlayers() {
    start("vote");
    act(owner, "END_GAME", Map.of());
    start("dice");
    assertEquals(2, views.get(owner.roomCode(), owner.playerToken()).players().size());
    assertEquals("dice", game(owner).get("gameId"));
  }

  @Test
  void midGameJoinRejectedAndLeaveReturnsToLobby() {
    start("vote");
    assertThrows(
        BusinessException.class,
        () -> rooms.join(owner.roomCode(), new Requests.Profile("迟到", "🐼")));
    act(guest, "LEAVE_ROOM", Map.of());
    assertEquals("WAITING", views.get(owner.roomCode(), owner.playerToken()).status());
  }

  @Test
  void truthOnlySelectedPlayerDraws() {
    start("truth");
    String selected = (String) ((List<?>) game(owner).get("selectedIds")).get(0);
    var chosen = selected.equals(owner.playerId()) ? owner : guest;
    var other = chosen == owner ? guest : owner;
    assertThrows(BusinessException.class, () -> act(other, "DRAW", Map.of("type", "truth")));
    act(chosen, "DRAW", Map.of("type", "dare"));
    assertEquals(true, game(chosen).get("complete"));
    assertNotNull(game(other).get("question"));
  }

  @Test
  void rouletteIndexMatchesLabelAndOnlySpinnerCanSpin() {
    start("roulette");
    assertThrows(BusinessException.class, () -> act(guest, "SPIN", Map.of()));
    act(owner, "SPIN", Map.of());
    int index = (int) game(owner).get("resultIndex");
    assertEquals(RouletteGameEngine.OPTIONS.get(index), game(owner).get("result"));
    if (index == 3) {
      act(owner, "SELECT_PLAYER", Map.of("targetPlayerId", guest.playerId()));
      assertEquals(true, game(owner).get("complete"));
    }
  }

  @Test
  void questionBankMeetsMinimumSizes() throws Exception {
    var vote = mapper.readTree(getClass().getResourceAsStream("/questions/vote_questions.json"));
    assertTrue(vote.size() >= 30);
    var truth = mapper.readTree(getClass().getResourceAsStream("/questions/truth_questions.json"));
    for (String mode : List.of("normal", "friends", "couple", "mellow"))
      for (String kind : List.of("truth", "dare"))
        assertTrue(truth.get(mode).get(kind).size() >= 20);
  }
}
