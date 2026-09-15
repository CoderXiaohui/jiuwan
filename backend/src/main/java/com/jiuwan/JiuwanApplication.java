package com.jiuwan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JiuwanApplication {
  public static void main(String[] args) {
    SpringApplication.run(JiuwanApplication.class, args);
  }
}
