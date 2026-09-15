package com.jiuwan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiuwan.repository.MemoryRoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomStorageProperties.class)
@Slf4j
public class RoomStorageConfiguration {
  @Bean
  @ConditionalOnProperty(name = "jiuwan.storage.mode", havingValue = "memory")
  MemoryRoomRepository memoryRoomRepository(ObjectMapper mapper, RoomStorageProperties properties) {
    log.info(
        "Room storage: memory, maxRooms={}, rooms are cleared on backend restart",
        properties.memoryMaxRooms());
    return new MemoryRoomRepository(mapper, properties.memoryMaxRooms());
  }
}
