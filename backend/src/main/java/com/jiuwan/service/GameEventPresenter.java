package com.jiuwan.service;

import com.jiuwan.domain.Room;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class GameEventPresenter {
  public List<Map<String, Object>> present(Room room) {
    if (room.getGameState() == null) return List.of();
    return room.getGameState().getEvents().stream()
        .map(
            e -> {
              String suffix =
                  switch (e.type()) {
                    case RoundWinnerEvent -> "zhajinhua".equals(room.getCurrentGameId())
                        ? "，赢下这一局！" : "默契 +1，配合满分！";
                    case PlayerSelectedEvent -> "，轮到你啦";
                    case GameFinishedEvent -> "，游戏结束";
                    default ->
                        switch (room.getSettings().getPunishmentMode()) {
                          case "truth" -> "，分享一个真心话吧";
                          case "dare" -> "，完成一个自选小任务吧";
                          default -> "，接受一个轻松挑战吧";
                        };
                  };
              if (e.type() == com.jiuwan.game.GameEvent.Type.RoundLoserEvent
                  && "zhajinhua".equals(room.getCurrentGameId())
                  && room.getGameState().getZhaJinHua().isDrinkMode())
                suffix = "，每人喝 1 小口；可用饮料代替或跳过";
              String names =
                  String.join(
                      "、",
                      e.playerIds().stream()
                          .map(
                              id ->
                                  room.getPlayers().containsKey(id)
                                      ? room.getPlayers().get(id).getNickname()
                                      : "离场玩家")
                          .toList());
              return Map.<String, Object>of(
                  "type", e.type().name(), "playerIds", e.playerIds(), "message", names + suffix);
            })
        .toList();
  }
}
