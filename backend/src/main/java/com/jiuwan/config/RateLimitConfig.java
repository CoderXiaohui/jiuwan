package com.jiuwan.config;

import com.jiuwan.exception.BusinessException;
import jakarta.servlet.http.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {
  private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                long minute = System.currentTimeMillis() / 60000;
                if (windows.size() > 4096)
                  windows.entrySet().removeIf(e -> e.getValue()[0] != minute);
                long[] bucket =
                    windows.compute(
                        request.getRemoteAddr(),
                        (key, value) ->
                            value == null || value[0] != minute
                                ? new long[] {minute, 1}
                                : new long[] {minute, value[1] + 1});
                BusinessException.require(bucket[1] <= 120, "RATE_LIMIT", "操作太频繁，请稍后再试");
                return true;
              }
            })
        .addPathPatterns("/api/**");
  }
}
