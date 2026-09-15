package com.jiuwan.config;

import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/** Memory mode must not create Redis clients, repositories, or health probes. */
public class RoomStorageAutoConfigurationFilter
    implements AutoConfigurationImportFilter, EnvironmentAware {
  private static final Set<String> REDIS_CONFIGURATIONS =
      Set.of(
          "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
          "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
          "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public boolean[] match(String[] classes, AutoConfigurationMetadata metadata) {
    boolean memory =
        "memory".equalsIgnoreCase(environment.getProperty("jiuwan.storage.mode", "redis"));
    boolean[] matches = new boolean[classes.length];
    for (int i = 0; i < classes.length; i++) {
      matches[i] = !memory || classes[i] == null || !REDIS_CONFIGURATIONS.contains(classes[i]);
    }
    return matches;
  }
}
