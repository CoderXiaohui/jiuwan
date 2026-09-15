package com.jiuwan.game;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class QuestionBank {
  private final JsonNode vote;
  private final JsonNode truth;
  private final JsonNode compatibility;

  public QuestionBank(ObjectMapper mapper) throws IOException {
    vote = read(mapper, "vote_questions.json");
    truth = read(mapper, "truth_questions.json");
    compatibility = read(mapper, "compatibility_questions.json");
  }

  private JsonNode read(ObjectMapper mapper, String name) throws IOException {
    try (var in = new ClassPathResource("questions/" + name).getInputStream()) {
      return mapper.readTree(in);
    }
  }

  public String vote(int index) {
    return vote.get(Math.floorMod(index, vote.size())).asText();
  }

  public String truth(String mode, String type, int index) {
    JsonNode choices = truth.path(mode).path(type);
    return choices.get(Math.floorMod(index, choices.size())).asText();
  }

  public JsonNode compatibility(int index) {
    return compatibility.get(Math.floorMod(index, compatibility.size()));
  }
}
