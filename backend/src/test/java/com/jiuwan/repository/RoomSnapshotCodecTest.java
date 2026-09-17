package com.jiuwan.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.domain.Room;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoomSnapshotCodecTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final RoomSnapshotCodec codec = new RoomSnapshotCodec(mapper);

  @Test
  void oldSettingsDefaultToOneBombAndNewSettingsPersist() {
    assertEquals(1, codec.decode("{\"settings\":{\"maxPlayers\":12}}").getSettings().getAngryBirdsBombCount());
    var room = new Room();
    room.getSettings().setAngryBirdsBombCount(6);
    assertEquals(6, codec.decode(codec.encode(room)).getSettings().getAngryBirdsBombCount());
  }

  @ParameterizedTest
  @CsvSource({"zhajinhua,dice,zhajinhua", ",dice,dice", ",,vote"})
  void oldSnapshotsRestoreSelectionFromCurrentThenPreviousGame(
      String current, String previous, String expected) throws Exception {
    var snapshot = mapper.createObjectNode();
    snapshot.put("currentGameId", current);
    snapshot.put("lastGameId", previous);
    assertEquals(expected, codec.decode(mapper.writeValueAsString(snapshot)).getSelectedGameId());
    snapshot.putNull("selectedGameId");
    assertEquals(expected, codec.decode(mapper.writeValueAsString(snapshot)).getSelectedGameId());
  }

  @Test
  void explicitLobbySelectionSurvivesRoundTripEvenWhenLastGameDiffers() {
    Room room = new Room();
    room.setSelectedGameId("zhajinhua");
    room.setLastGameId("dice");
    var restored = codec.decode(codec.encode(room));
    assertEquals("zhajinhua", restored.getSelectedGameId());
    assertEquals("dice", restored.getLastGameId());
  }
}
