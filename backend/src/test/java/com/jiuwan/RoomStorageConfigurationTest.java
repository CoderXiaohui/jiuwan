package com.jiuwan;

import static org.assertj.core.api.Assertions.assertThat;

import com.jiuwan.repository.MemoryRoomRepository;
import com.jiuwan.repository.RedisRoomRepository;
import com.jiuwan.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RoomStorageConfigurationTest {
  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(JiuwanApplication.class)
          .withPropertyValues("spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1");

  @ParameterizedTest
  @ValueSource(strings = {"memory", "MEMORY"})
  void memoryStartsHealthyWithoutRedisClients(String mode) {
    contextRunner
        .withPropertyValues("jiuwan.storage.mode=" + mode)
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(RoomRepository.class)
                  .hasSingleBean(MemoryRoomRepository.class)
                  .doesNotHaveBean(RedisRoomRepository.class)
                  .doesNotHaveBean(RedisConnectionFactory.class)
                  .doesNotHaveBean(StringRedisTemplate.class);
              assertThat(context.getBean(HealthEndpoint.class).health().getStatus())
                  .isEqualTo(Status.UP);
            });
  }

  @Test
  void defaultStorageIsRedis() {
    contextRunner.run(
        context -> {
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(RoomRepository.class)
              .hasSingleBean(RedisRoomRepository.class)
              .hasSingleBean(RedisConnectionFactory.class)
              .doesNotHaveBean(MemoryRoomRepository.class);
        });
  }

  @Test
  void explicitRedisUsesRedis() {
    contextRunner
        .withPropertyValues("jiuwan.storage.mode=redis")
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(RedisRoomRepository.class)
                  .doesNotHaveBean(MemoryRoomRepository.class);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"memroy", "", "sqlite"})
  void invalidModeFailsInsteadOfFallingBack(String mode) {
    contextRunner
        .withPropertyValues("jiuwan.storage.mode=" + mode)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void nonpositiveMemoryCapacityFailsStartup() {
    contextRunner
        .withPropertyValues("jiuwan.storage.mode=memory", "jiuwan.storage.memory-max-rooms=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
