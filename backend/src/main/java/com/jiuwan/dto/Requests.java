package com.jiuwan.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public final class Requests {
  private Requests() {}

  public record Profile(
      @NotBlank @Size(max = 16) String nickname, @NotBlank @Size(max = 8) String avatar) {}

  public record Start(@NotBlank @Size(max = 80) String requestId) {}

  public record Command(
      String type,
      String requestId,
      String roomCode,
      String playerId,
      String playerToken,
      String gameId,
      String instanceId,
      String action,
      Map<String, Object> data) {}
}
