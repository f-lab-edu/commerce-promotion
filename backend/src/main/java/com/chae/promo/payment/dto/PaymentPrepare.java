package com.chae.promo.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentPrepare {

    @NotBlank
    private String orderId; // 주문 ID
}
