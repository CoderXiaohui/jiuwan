package com.jiuwan.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jiuwan.storage")
public record RoomStorageProperties(
    @DefaultValue("redis") @Pattern(regexp = "(?i:memory|redis)") String mode,
    @DefaultValue("1000") @Min(1) int memoryMaxRooms) {}
