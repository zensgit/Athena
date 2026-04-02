package com.ecm.core.dto;

import com.ecm.core.entity.LockLifetime;
import com.ecm.core.entity.LockStatus;
import com.ecm.core.entity.LockType;

import java.time.LocalDateTime;

public record LockInfoDto(
    LockStatus status,
    String lockedBy,
    LocalDateTime lockedDate,
    LockLifetime lockLifetime,
    LocalDateTime lockExpiresAt,
    LockType lockType,
    String additionalInfo,
    boolean lockDeep,
    Long remainingSeconds,
    Long lockAgeSeconds,
    boolean canUnlock
) {
}
