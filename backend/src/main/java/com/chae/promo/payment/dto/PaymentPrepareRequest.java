package com.chae.promo.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PaymentPrepareRequest(
        @NotBlank
        String orderId, // 주문 ID

        @NotNull
        @Size(min = 1)
        List<PaymentMethodRequest>payments
) {
}
