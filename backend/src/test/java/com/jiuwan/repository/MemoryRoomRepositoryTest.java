package com.jiuwan.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.Room;
import com.jiuwan.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MemoryRoomRepositoryTest {
  private final AtomicLong now = new AtomicLong(1000000);
  private final Clock clock = mock(Clock.class);

  private MemoryRoomRepository repository(int capacity) {
    when(clock.millis()).thenAnswer(invocation -> now.get());
    return new MemoryRoomRepository(new ObjectMapper(), capacity, clock);
  }

  private Room room(String code) {
    Room room = new Room();
    room.setRoomCode(code);
    room.getProcessedRequests().add("player:request-1");
    return room;
  }

  @Test
  void mutationsAreIsolatedUntilSaveAndDuplicateCreatePreservesExistingRoom() {
    var repository = repository(2);
    Room original = room("123456");
    assertTrue(repository.create(original));
    original.getProcessedRequests().clear();
    original.getSettings().setMaxPlayers(3);
    Room loaded = repository.find("123456");
    assertEquals(Set.of("player:request-1"), loaded.getProcessedRequests());
    assertEquals(12, loaded.getSettings().getMaxPlayers());
    loaded.getSettings().setMaxPlayers(5);
    assertEquals(12, repository.find("123456").getSettings().getMaxPlayers());
    repository.save(loaded);
    loaded.getSettings().setMaxPlayers(8);
    assertEquals(5, repository.find("123456").getSettings().getMaxPlayers());
    assertFalse(repository.create(room("123456")));
    assertEquals(5, repository.find("123456").getSettings().getMaxPlayers());
  }

  @Test
  void activeRoomExpiresAfterSixHoursAndSaveRefreshesExpiry() {
    var repository = repository(2);
    Room room = room("123456");
    repository.create(room);
    now.addAndGet(Duration.ofHours(5).toMillis());
    repository.save(room);
    now.addAndGet(Duration.ofHours(6).toMillis() - 1);
    assertNotNull(repository.find("123456"));
    now.incrementAndGet();
    assertNull(repository.find("123456"));
    assertTrue(repository.create(room("123456")));
  }

  @Test
  void closedRoomsExpireAfterFifteenMinutesAndSweepRemovesUnvisitedRooms() {
    var repository = repository(2);
    Room closed = room("123456");
    repository.create(closed);
    closed.setStatus(Room.Status.CLOSED);
    repository.save(closed);
    repository.create(room("234567"));
    now.addAndGet(Duration.ofMinutes(15).toMillis() - 1);
    assertEquals(Room.Status.CLOSED, repository.find("123456").getStatus());
    now.incrementAndGet();
    repository.removeExpired();
    assertEquals(Set.of("234567"), repository.codes());
    assertNull(repository.find("123456"));
    now.addAndGet(Duration.ofHours(6).toMillis());
    repository.removeExpired();
    assertTrue(repository.codes().isEmpty());
  }

  @Test
  void capacityRejectsNewRoomsWithoutEvictingOrBlockingUpdatesAndReclaimsExpiry() {
    var repository = repository(1);
    Room first = room("123456");
    repository.create(first);
    assertFalse(repository.create(room("123456")));
    BusinessException error =
        assertThrows(BusinessException.class, () -> repository.create(room("234567")));
    assertEquals("SERVICE_UNAVAILABLE", error.getCode());
    assertThrows(BusinessException.class, () -> repository.save(room("234567")));
    first.setStatus(Room.Status.CLOSED);
    repository.save(first);
    assertEquals(Room.Status.CLOSED, repository.find("123456").getStatus());
    now.addAndGet(Duration.ofMinutes(15).toMillis());
    assertTrue(repository.create(room("234567")));
    assertEquals(Set.of("234567"), repository.codes());
  }

  @Test
  void concurrentCreateReservesRoomCodeOnlyOnce() throws Exception {
    var repository = repository(10);
    List<Callable<Boolean>> attempts = new ArrayList<>();
    for (int i = 0; i < 64; i++) attempts.add(() -> repository.create(room("123456")));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      int created = 0;
      for (var result : executor.invokeAll(attempts)) if (result.get()) created++;
      assertEquals(1, created);
    }
  }

  @Test
  void concurrentCreatesCannotExceedCapacity() throws Exception {
    var repository = repository(8);
    List<Callable<Boolean>> attempts = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      String code = String.valueOf(100000 + i);
      attempts.add(
          () -> {
            try {
              return repository.create(room(code));
            } catch (BusinessException e) {
              assertEquals("SERVICE_UNAVAILABLE", e.getCode());
              return false;
            }
          });
    }
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      int created = 0;
      for (var result : executor.invokeAll(attempts)) if (result.get()) created++;
      assertEquals(8, created);
      assertEquals(8, repository.codes().size());
    }
  }
}
