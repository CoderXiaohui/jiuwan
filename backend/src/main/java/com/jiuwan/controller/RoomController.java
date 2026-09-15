package com.jiuwan.controller;

import com.jiuwan.dto.*;
import com.jiuwan.game.GameRegistry;
import com.jiuwan.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoomController {
  private final RoomService rooms;
  private final RoomViewService views;
  private final GameRegistry registry;
  private final GameService games;

  @PostMapping("/rooms")
  public ApiResponse<?> create(@Valid @RequestBody Requests.Profile profile) {
    return ApiResponse.ok(rooms.create(profile));
  }

  @PostMapping("/rooms/{code}/join")
  public ApiResponse<?> join(
      @PathVariable String code, @Valid @RequestBody Requests.Profile profile) {
    return ApiResponse.ok(rooms.join(code, profile));
  }

  @GetMapping("/rooms/{code}")
  public ApiResponse<?> get(
      @PathVariable String code,
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return ApiResponse.ok(auth == null ? views.preview(code) : views.get(code, bearer(auth)));
  }

  @GetMapping("/games")
  public ApiResponse<?> games() {
    return ApiResponse.ok(registry.list());
  }

  @PostMapping("/rooms/{code}/games/{gameId}/start")
  public ApiResponse<?> start(
      @PathVariable String code,
      @PathVariable String gameId,
      @RequestHeader("Authorization") String auth,
      @Valid @RequestBody Requests.Start body) {
    games.start(code, bearer(auth), gameId, body.requestId());
    return ApiResponse.ok(views.get(code, bearer(auth)));
  }

  private String bearer(String auth) {
    return auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : "";
  }
}
