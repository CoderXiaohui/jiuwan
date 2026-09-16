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
import com.jiuwan.repository.MemoryRoomRepository;
import com.jiuwan.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                new ZhaJinHuaGameEngine(random),
                new BigSmallGameEngine(random)));
    rooms =
        new RoomService(new MemoryRoomRepository(mapper, 1000), identity, random, registry, event -> {});
    games = new GameService(rooms, registry, mapper, identity);
    views = new RoomViewService(rooms, identity, registry, new GameEventPresenter());
    owner = rooms.create(new Requests.Profile("小辉", "😎"));
    guest = rooms.join(owner.roomCode(), new Requests.Profile("老王", "🐼"));
    rooms.reconnect(owner.roomCode(), owner.playerToken());
    rooms.reconnect(owner.roomCode(), guest.playerToken());
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

  private void selectGame(RoomView.Credentials player, String gameId) {
    games.command(
        owner.roomCode(),
        player.playerToken(),
        command(gameId, null, "SELECT_GAME", Map.of(), UUID.randomUUID().toString()));
  }

  @Test
  void creationSupportsInitialSelectionAndRejectsUnknownGamesBeforeCreatingRoom() {
    assertEquals("vote", views.get(owner.roomCode(), owner.playerToken()).selectedGameId());
    var created = rooms.create(new Requests.Profile("新房主", "😎"), "zhajinhua");
    assertEquals("zhajinhua", views.get(created.roomCode(), created.playerToken()).selectedGameId());
    assertNull(rooms.read(created.roomCode(), Room::getLastGameId));
    var codes = rooms.activeCodes();
    assertEquals(
        "INVALID_GAME",
        assertThrows(
                BusinessException.class,
                () -> rooms.create(new Requests.Profile("新房主", "😎"), "unknown"))
            .getCode());
    assertEquals(codes, rooms.activeCodes());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"vote", "dice", "roulette", "truth", "compatibility", "zhajinhua", "big-small"})
  void selectionIsSharedWithLateJoinersAndSurvivesReconnectAndRecovery(String gameId) {
    long version = views.get(owner.roomCode(), owner.playerToken()).version();
    selectGame(owner, gameId);
    RoomView selected = views.get(owner.roomCode(), owner.playerToken());
    assertEquals(version + 1, selected.version());
    assertEquals(gameId, selected.selectedGameId());
    assertEquals("WAITING", selected.status());
    assertNull(selected.currentGameId());
    assertNull(selected.game());
    assertNull(rooms.read(owner.roomCode(), Room::getLastGameId));
    assertEquals(gameId, views.get(owner.roomCode(), guest.playerToken()).selectedGameId());

    var late = rooms.join(owner.roomCode(), new Requests.Profile("迟到", "🐼"));
    assertEquals(gameId, views.get(owner.roomCode(), late.playerToken()).selectedGameId());
    rooms.disconnect(owner.roomCode(), guest.playerId());
    rooms.reconnect(owner.roomCode(), guest.playerToken());
    rooms.recover();
    rooms.reconnect(owner.roomCode(), owner.playerToken());
    rooms.reconnect(owner.roomCode(), guest.playerToken());
    assertEquals(gameId, views.get(owner.roomCode(), owner.playerToken()).selectedGameId());
    assertEquals(gameId, views.get(owner.roomCode(), guest.playerToken()).selectedGameId());
  }

  @Test
  void duplicateSelectionCannotIncrementVersionOrUndoLaterSelection() {
    var selection = command("zhajinhua", null, "SELECT_GAME", Map.of(), "select-once");
    games.command(owner.roomCode(), owner.playerToken(), selection);
    var first = views.get(owner.roomCode(), owner.playerToken());
    games.command(owner.roomCode(), owner.playerToken(), selection);
    assertEquals(first, views.get(owner.roomCode(), owner.playerToken()));
    selectGame(owner, "dice");
    var latest = views.get(owner.roomCode(), owner.playerToken());
    games.command(owner.roomCode(), owner.playerToken(), selection);
    assertEquals(latest, views.get(owner.roomCode(), owner.playerToken()));
  }

  @Test
  void selectionRequiresOwnerValidGameAndLobbyWithoutChangingStateOnFailure() {
    var initial = views.get(owner.roomCode(), owner.playerToken());
    assertEquals(
        "OWNER_ONLY",
        assertThrows(BusinessException.class, () -> selectGame(guest, "zhajinhua")).getCode());
    for (String invalid : Arrays.asList(null, "", "unknown"))
      assertEquals(
          "INVALID_GAME",
          assertThrows(BusinessException.class, () -> selectGame(owner, invalid)).getCode());
    assertEquals(initial, views.get(owner.roomCode(), owner.playerToken()));

    start("zhajinhua");
    var playing = views.get(owner.roomCode(), owner.playerToken());
    assertEquals(
        "GAME_IN_PROGRESS",
        assertThrows(BusinessException.class, () -> selectGame(owner, "dice")).getCode());
    assertEquals(playing, views.get(owner.roomCode(), owner.playerToken()));
  }

  @Test
  void returningToLobbyKeepsSelectionAndBrowsingDoesNotChangeCountdownHistory() {
    start("dice");
    act(owner, "END_GAME", Map.of());
    assertEquals("dice", views.get(owner.roomCode(), guest.playerToken()).selectedGameId());
    selectGame(owner, "zhajinhua");
    assertEquals("dice", rooms.read(owner.roomCode(), Room::getLastGameId));
    selectGame(owner, "dice");
    long before = System.currentTimeMillis();
    games.start(owner.roomCode(), owner.playerToken(), "dice", "same-after-browsing");
    assertStartWindow(before, System.currentTimeMillis(), 0);

    act(owner, "END_GAME", Map.of());
    selectGame(owner, "zhajinhua");
    before = System.currentTimeMillis();
    games.start(owner.roomCode(), owner.playerToken(), "zhajinhua", "different-after-browsing");
    assertStartWindow(before, System.currentTimeMillis(), 3000);
    assertEquals("zhajinhua", views.get(owner.roomCode(), guest.playerToken()).selectedGameId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"LEAVE_ROOM", "KICK_PLAYER"})
  void selectionSurvivesInterruptedGamesAndOwnershipTransfer(String action) {
    start("zhajinhua");
    act(owner, action, Map.of("targetPlayerId", guest.playerId()));
    var remaining = "LEAVE_ROOM".equals(action) ? guest : owner;
    var lobby = views.get(owner.roomCode(), remaining.playerToken());
    assertEquals("WAITING", lobby.status());
    assertNull(lobby.game());
    assertEquals("zhajinhua", lobby.selectedGameId());
    assertEquals(remaining.playerId(), lobby.ownerId());
    selectGame(remaining, "dice");
    assertEquals("dice", views.get(owner.roomCode(), remaining.playerToken()).selectedGameId());
  }

  private void finishRound(String gameId) {
    switch (gameId) {
      case "vote" -> {
        act(owner, "VOTE", Map.of("targetPlayerId", guest.playerId()));
        act(guest, "VOTE", Map.of("targetPlayerId", owner.playerId()));
      }
      case "dice" -> {
        act(owner, "SHAKE_DICE", Map.of());
        act(guest, "SHAKE_DICE", Map.of());
      }
      case "roulette" -> {
        act(owner, "SPIN", Map.of());
        if (Boolean.TRUE.equals(game(owner).get("needsSelection")))
          act(owner, "SELECT_PLAYER", Map.of("targetPlayerId", guest.playerId()));
      }
      case "truth" -> {
        var selected = (List<?>) game(owner).get("selectedIds");
        act(selected.contains(owner.playerId()) ? owner : guest, "DRAW", Map.of("type", "truth"));
      }
      case "compatibility" -> {
        Object answer = ((List<?>) game(owner).get("options")).getFirst();
        act(owner, "ANSWER", Map.of("answer", answer));
        act(guest, "ANSWER", Map.of("answer", answer));
      }
      case "zhajinhua" -> {
        var poker = mapper.valueToTree(game(owner)).get("poker");
        var current = poker.get("currentPlayerId").asText();
        act(
            current.equals(owner.playerId()) ? owner : guest,
            "FOLD",
            Map.of("turnNumber", poker.get("turnNumber").asInt()));
      }
      case "big-small" -> act(owner, "END_ROUND", Map.of());
      default -> fail("Missing countdown coverage for " + gameId);
    }
    assertEquals(true, game(owner).get("complete"));
  }

  private void assertStartWindow(long before, long after, long delay) {
    long startsAt = (long) game(owner).get("startsAt");
    assertTrue(startsAt >= before + delay && startsAt <= after + delay);
    assertEquals(startsAt, game(guest).get("startsAt"));
    long duration =
        switch ((String) game(owner).get("gameId")) {
          case "vote" -> 45000;
          case "zhajinhua" -> 30000;
          case "big-small" -> 0;
          default -> 60000;
        };
    assertEquals(duration == 0 ? 0L : startsAt + duration, game(owner).get("deadline"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"vote", "dice", "roulette", "truth", "compatibility", "zhajinhua", "big-small"})
  void countdownOnlyAppliesToFirstGameOrGameChanges(String gameId) {
    long before = System.currentTimeMillis();
    games.start(owner.roomCode(), owner.playerToken(), gameId, "first");
    assertStartWindow(before, System.currentTimeMillis(), 3000);
    assertEquals(
        "COUNTDOWN", assertThrows(BusinessException.class, () -> finishRound(gameId)).getCode());

    rooms.mutate(
        owner.roomCode(),
        room -> {
          room.getGameState().setStartsAt(0);
          // Existing persisted rooms can lack lastGameId and still skip the next-round countdown.
          room.setLastGameId(null);
          return null;
        },
        null);
    finishRound(gameId);
    String previousInstance = (String) game(owner).get("instanceId");
    before = System.currentTimeMillis();
    act(owner, "NEXT_ROUND", Map.of());
    assertStartWindow(before, System.currentTimeMillis(), 0);
    assertEquals(2, game(owner).get("round"));
    assertNotEquals(previousInstance, game(owner).get("instanceId"));
    finishRound(gameId);

    act(owner, "END_GAME", Map.of());
    assertNull(game(owner));
    rooms.disconnect(owner.roomCode(), guest.playerId());
    rooms.reconnect(owner.roomCode(), guest.playerToken());
    before = System.currentTimeMillis();
    games.start(owner.roomCode(), owner.playerToken(), gameId, "same-from-lobby");
    assertStartWindow(before, System.currentTimeMillis(), 0);
    assertEquals(1, game(owner).get("round"));
    finishRound(gameId);

    act(owner, "END_GAME", Map.of());
    String otherGameId = "dice".equals(gameId) ? "vote" : "dice";
    before = System.currentTimeMillis();
    games.start(owner.roomCode(), owner.playerToken(), otherGameId, "different-game");
    assertStartWindow(before, System.currentTimeMillis(), 3000);
    act(owner, "END_GAME", Map.of());
    before = System.currentTimeMillis();
    games.start(owner.roomCode(), owner.playerToken(), gameId, "switch-back");
    assertStartWindow(before, System.currentTimeMillis(), 3000);
    assertEquals(
        "COUNTDOWN", assertThrows(BusinessException.class, () -> finishRound(gameId)).getCode());
  }

  @Test
  void failedStartDoesNotChangePreviousGame() {
    start("dice");
    act(owner, "END_GAME", Map.of());
    rooms.disconnect(owner.roomCode(), guest.playerId());
    assertEquals(
        "NEED_PLAYERS",
        assertThrows(
                BusinessException.class,
                () -> games.start(owner.roomCode(), owner.playerToken(), "vote", "failed-start"))
            .getCode());
    assertEquals("dice", views.get(owner.roomCode(), owner.playerToken()).selectedGameId());
    rooms.reconnect(owner.roomCode(), guest.playerToken());
    long before = System.currentTimeMillis();
    games.start(owner.roomCode(), owner.playerToken(), "dice", "retry-same-game");
    assertStartWindow(before, System.currentTimeMillis(), 0);
    finishRound("dice");
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
    selectGame(owner, "zhajinhua");
    rooms.disconnect(owner.roomCode(), owner.playerId());
    rooms.maintain(owner.roomCode(), System.currentTimeMillis() + 16000, games::timeout);
    assertEquals(guest.playerId(), views.get(owner.roomCode(), guest.playerToken()).ownerId());
    assertEquals("zhajinhua", views.get(owner.roomCode(), guest.playerToken()).selectedGameId());
    selectGame(guest, "dice");
    assertEquals("dice", views.get(owner.roomCode(), owner.playerToken()).selectedGameId());
  }

  @Test
  void idleRoomCloses() {
    rooms.disconnect(owner.roomCode(), owner.playerId());
    rooms.disconnect(owner.roomCode(), guest.playerId());
    rooms.maintain(owner.roomCode(), System.currentTimeMillis() + 1800001, games::timeout);
    assertThrows(BusinessException.class, () -> views.preview(owner.roomCode()));
  }

  @Test
  void registryDiscoversSevenPluginsAndRejectsDuplicates() {
    assertEquals(7, registry.list().size());
    assertEquals("大的喝小的喝", registry.get("big-small").gameName());
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
    assertEquals(
        "STALE_ACTION",
        assertThrows(
                BusinessException.class,
                () ->
                    games.command(
                        owner.roomCode(),
                        owner.playerToken(),
                        command(
                            "zhajinhua",
                            instance,
                            "CALL",
                            Map.of("turnNumber", 1),
                            "old-poker-call")))
            .getCode());
  }

  @Test
  void bigSmallPersistsPrivateViewsAndEndRequestsAreIdempotentAndBoundToTheRound() {
    start("big-small");
    String instance = (String) game(owner).get("instanceId");
    var ownerView = mapper.valueToTree(game(owner));
    var guestView = mapper.valueToTree(game(guest));
    assertFalse(ownerView.at("/bigSmall/seats/0").has("card"));
    assertTrue(ownerView.at("/bigSmall/seats/1").has("card"));
    assertFalse(guestView.at("/bigSmall/seats/1").has("card"));
    assertTrue(guestView.at("/bigSmall/seats/0").has("card"));
    rooms.disconnect(owner.roomCode(), owner.playerId());
    rooms.reconnect(owner.roomCode(), owner.playerToken());
    assertEquals(ownerView, mapper.valueToTree(game(owner)));
    assertEquals(
        "ROUND_ACTIVE",
        assertThrows(BusinessException.class, () -> act(owner, "NEXT_ROUND", Map.of())).getCode());
    assertEquals(
        "OWNER_ONLY",
        assertThrows(BusinessException.class, () -> act(guest, "END_ROUND", Map.of())).getCode());
    var end = command("big-small", instance, "END_ROUND", Map.of(), "end-round");
    games.command(owner.roomCode(), owner.playerToken(), end);
    long version = views.get(owner.roomCode(), owner.playerToken()).version();
    games.command(owner.roomCode(), owner.playerToken(), end);
    assertEquals(version, views.get(owner.roomCode(), owner.playerToken()).version());
    assertEquals(true, game(owner).get("complete"));
    assertEquals(game(owner), game(guest));
    var revealed = mapper.valueToTree(game(owner));
    assertEquals(guestView.at("/bigSmall/seats/0/card"), revealed.at("/bigSmall/seats/0/card"));
    assertEquals(ownerView.at("/bigSmall/seats/1/card"), revealed.at("/bigSmall/seats/1/card"));
    assertTrue(views.get(owner.roomCode(), owner.playerToken()).events().isEmpty());
    act(owner, "NEXT_ROUND", Map.of());
    assertEquals(false, game(owner).get("complete"));
    assertFalse(mapper.valueToTree(game(owner)).at("/bigSmall/seats/0").has("card"));
    assertEquals(
        "STALE_ACTION",
        assertThrows(
                BusinessException.class,
                () ->
                    games.command(
                        owner.roomCode(),
                        owner.playerToken(),
                        command("big-small", instance, "END_ROUND", Map.of(), "stale-end")))
            .getCode());
    act(owner, "END_GAME", Map.of());
    assertNull(game(owner));
  }

  @Test
  void bigSmallMaintenanceSkipsTimeoutAndAllowsManualEndAfterSixtySeconds() {
    start("big-small");
    long now = System.currentTimeMillis() + 70000;
    rooms.mutate(
        owner.roomCode(),
        room -> {
          room.getPlayers().values().forEach(p -> p.setLastSeen(now));
          return null;
        },
        null);
    rooms.maintain(owner.roomCode(), now, room -> fail("Untimed game must never be ticked"));
    assertEquals(false, game(owner).get("complete"));
    assertEquals(0L, game(owner).get("deadline"));
    act(owner, "END_ROUND", Map.of());
    assertEquals(true, game(owner).get("complete"));
  }

  @Test
  void bigSmallNewOwnerCanRevealEvenWhenNotDealtIntoTheRound() {
    var spectator = rooms.join(owner.roomCode(), new Requests.Profile("阿琳", "🦊"));
    start("big-small");
    rooms.reconnect(owner.roomCode(), spectator.playerToken());
    var spectatorView = mapper.valueToTree(game(spectator));
    assertFalse(spectatorView.at("/bigSmall/seats/0").has("card"));
    assertFalse(spectatorView.at("/bigSmall/seats/1").has("card"));
    rooms.disconnect(owner.roomCode(), owner.playerId());
    rooms.disconnect(owner.roomCode(), guest.playerId());
    rooms.maintain(owner.roomCode(), System.currentTimeMillis() + 16000, games::timeout);
    assertEquals(
        spectator.playerId(), views.get(owner.roomCode(), spectator.playerToken()).ownerId());
    assertEquals(
        "OWNER_ONLY",
        assertThrows(BusinessException.class, () -> act(owner, "END_ROUND", Map.of())).getCode());
    act(spectator, "END_ROUND", Map.of());
    assertEquals(true, game(spectator).get("complete"));
    assertEquals(game(owner), game(spectator));
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
