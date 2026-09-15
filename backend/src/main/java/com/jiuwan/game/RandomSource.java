package com.jiuwan.game;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomSource {
  private final SecureRandom random = new SecureRandom();

  public int nextInt(int bound) {
    return random.nextInt(bound);
  }
}
