package com.ecm.core.dto;

import com.ecm.core.entity.CheckoutStatus;

import java.time.LocalDateTime;

public record CheckoutInfoDto(
    CheckoutStatus status,
    String checkoutUser,
    LocalDateTime checkoutDate,
    Long checkoutAgeSeconds,
    boolean canCheckout,
    boolean canCheckIn,
    boolean canCancelCheckout,
    boolean canKeepCheckedOut,
    boolean requiresNewVersionFile,
    String blockingReason
) {
}
