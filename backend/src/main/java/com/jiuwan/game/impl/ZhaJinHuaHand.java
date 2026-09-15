package com.jiuwan.game.impl;

import java.util.*;

/** Card IDs 0..51: spades, hearts, clubs, diamonds, each ordered 2..A. */
public final class ZhaJinHuaHand {
  private ZhaJinHuaHand() {}

  public record Value(int category, List<Integer> ranks, boolean special235) {
    public String label() {
      return List.of("散牌", "对子", "顺子", "金花", "顺金", "豹子").get(category);
    }
  }

  public static Value evaluate(List<Integer> cards) {
    if (cards.size() != 3 || new HashSet<>(cards).size() != 3
        || cards.stream().anyMatch(card -> card < 0 || card >= 52))
      throw new IllegalArgumentException("A hand needs three distinct cards from a 52-card deck");
    List<Integer> ranks = cards.stream().map(card -> card % 13 + 2)
        .sorted(Comparator.reverseOrder()).toList();
    boolean flush = cards.stream().map(card -> card / 13).distinct().count() == 1;
    int high = ranks.get(0), middle = ranks.get(1), low = ranks.get(2);
    boolean wheel = ranks.equals(List.of(14, 3, 2));
    boolean straight = wheel || (high == middle + 1 && middle == low + 1);
    if (high == low) return new Value(5, List.of(high), false);
    if (flush && straight) return new Value(4, List.of(wheel ? 3 : high), false);
    if (flush) return new Value(3, ranks, false);
    if (straight) return new Value(2, List.of(wheel ? 3 : high), false);
    if (high == middle || middle == low)
      return new Value(1, List.of(middle, high == middle ? low : high), false);
    return new Value(0, ranks, ranks.equals(List.of(5, 3, 2)));
  }

  /** 235 is a pairwise exception, not a sortable global ranking. Ties return zero. */
  public static int compare(List<Integer> left, List<Integer> right, boolean special235) {
    Value a = evaluate(left), b = evaluate(right);
    if (special235 && a.special235() && b.category() == 5) return 1;
    if (special235 && b.special235() && a.category() == 5) return -1;
    int category = Integer.compare(a.category(), b.category());
    if (category != 0) return category;
    for (int i = 0; i < a.ranks().size(); i++) {
      int rank = Integer.compare(a.ranks().get(i), b.ranks().get(i));
      if (rank != 0) return rank;
    }
    return 0;
  }

  public static List<Map<String, Object>> display(List<Integer> cards) {
    return cards.stream().sorted(Comparator.comparingInt((Integer card) -> card % 13).reversed())
        .map(card -> Map.<String, Object>of("rank", card % 13 + 2,
            "suit", List.of("spades", "hearts", "clubs", "diamonds").get(card / 13))).toList();
  }
}
