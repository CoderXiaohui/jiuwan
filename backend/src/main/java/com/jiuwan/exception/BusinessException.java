package com.jiuwan.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
  private final String code;

  public BusinessException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static void require(boolean condition, String code, String message) {
    if (!condition) throw new BusinessException(code, message);
  }
}
