package com.jiuwan.service;

import com.jiuwan.domain.*;
import com.jiuwan.dto.RoomView;
import com.jiuwan.game.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomViewService {
  private final RoomService rooms;
  private final IdentityService identity;
  private final GameRegistry registry;
  private final GameEventPresenter presenter;

  public RoomView get(String code, String token) {
    return rooms.read(
        code,
        room -> {
          Player viewer = identity.authenticate(room, token);
          var players =
              room.getPlayers().values().stream()
                  .map(
                      p ->
                          new RoomView.PlayerView(
                              p.getPlayerId(),
                              p.getNickname(),
                              p.getAvatar(),
                              p.isConnected(),
                              room.getOwnerId().equals(p.getPlayerId()),
                              p.getJoinedAt(),
                              p.getScore()))
                  .toList();
          Map<String, Object> game =
              room.getGameState() == null
                  ? null
                  : registry
                      .get(room.getCurrentGameId())
                      .getPlayerView(new GameContext(room), viewer);
          return new RoomView(
              room.getRoomId(),
              code,
              room.getOwnerId(),
              room.getStatus().name(),
              room.getSelectedGameId(),
              room.getCurrentGameId(),
              room.getCreatedAt(),
              room.getGameStateVersion(),
              players,
              room.getSettings(),
              game,
              presenter.present(room));
        });
  }

  public Map<String, Object> preview(String code) {
    return rooms.read(
        code,
        room ->
            Map.of(
                "roomCode",
                code,
                "status",
                room.getStatus(),
                "playerCount",
                room.getPlayers().size(),
                "maxPlayers",
                room.getSettings().getMaxPlayers()));
  }
}
