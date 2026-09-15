package com.jiuwan.dto;

public record ApiResponse<T>(boolean success, T data, ApiError error) {
  public record ApiError(String code, String message) {}

  public static <T> ApiResponse<T> ok(T value) {
    return new ApiResponse<>(true, value, null);
  }

  public static ApiResponse<Void> fail(String code, String message) {
    return new ApiResponse<>(false, null, new ApiError(code, message));
  }
}
