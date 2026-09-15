package com.jiuwan.service;

import static com.jiuwan.exception.BusinessException.require;

import com.jiuwan.domain.*;
import com.jiuwan.dto.Requests.Profile;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
  public static final List<String> AVATARS =
      List.of("😎", "🤡", "🐶", "🐱", "👻", "🔥", "🍺", "🥳", "🦊", "🐼", "👽", "🪩");
  private final SecureRandom random = new SecureRandom();

  public String token() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public Player create(Profile profile, String token) {
    Player p = new Player();
    p.setPlayerId(UUID.randomUUID().toString());
    p.setTokenHash(hash(token));
    p.setJoinedAt(System.currentTimeMillis());
    p.setLastSeen(p.getJoinedAt());
    update(p, profile);
    return p;
  }

  public void update(Player player, Profile profile) {
    require(
        profile.nickname() != null
            && !profile.nickname().isBlank()
            && profile.nickname().strip().length() <= 16,
        "INVALID_NICKNAME",
        "昵称需要 1–16 个字符");
    require(AVATARS.contains(profile.avatar()), "INVALID_AVATAR", "请选择一个有效头像");
    player.setNickname(profile.nickname().strip());
    player.setAvatar(profile.avatar());
  }

  public Player authenticate(Room room, String token) {
    require(
        token != null && token.length() >= 32 && token.length() <= 128,
        "INVALID_PLAYER",
        "身份已失效，请重新加入房间");
    String hash = hash(token);
    return room.getPlayers().values().stream()
        .filter(
            p ->
                MessageDigest.isEqual(
                    p.getTokenHash().getBytes(StandardCharsets.UTF_8),
                    hash.getBytes(StandardCharsets.UTF_8)))
        .findFirst()
        .orElseThrow(
            () -> new com.jiuwan.exception.BusinessException("INVALID_PLAYER", "身份已失效，请重新加入房间"));
  }
}
