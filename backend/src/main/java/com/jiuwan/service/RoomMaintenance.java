package com.jiuwan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomMaintenance {
  private final RoomService rooms;
  private final GameService games;

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    rooms.recover();
  }

  @Scheduled(fixedDelay = 1000)
  public void tick() {
    for (String code : rooms.activeCodes())
      try {
        rooms.maintain(code, System.currentTimeMillis(), games::timeout);
      } catch (Exception e) {
        log.error("Room maintenance failed code={}", code, e);
      }
  }
}
