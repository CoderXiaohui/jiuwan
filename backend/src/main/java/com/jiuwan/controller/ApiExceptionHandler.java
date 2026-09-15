package com.jiuwan.controller;

import com.jiuwan.dto.ApiResponse;
import com.jiuwan.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> business(BusinessException e) {
    var status =
        switch (e.getCode()) {
          case "ROOM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "INVALID_PLAYER" -> HttpStatus.UNAUTHORIZED;
          case "OWNER_ONLY" -> HttpStatus.FORBIDDEN;
          case "RATE_LIMIT" -> HttpStatus.TOO_MANY_REQUESTS;
          case "SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status).body(ApiResponse.fail(e.getCode(), e.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<?> validation(Exception e) {
    return ResponseEntity.badRequest().body(ApiResponse.fail("INVALID_INPUT", "请检查输入内容"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> unexpected(Exception e) {
    log.error("API request failed", e);
    return ResponseEntity.status(503)
        .body(ApiResponse.fail("SERVICE_UNAVAILABLE", "服务暂时不可用，请稍后重试"));
  }
}
