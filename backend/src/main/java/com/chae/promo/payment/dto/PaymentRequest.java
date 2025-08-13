package com.chae.promo.payment.dto;

import com.chae.promo.payment.entity.PgType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentRequest {

    @NotBlank
    private String orderId; // 주문 ID

    @NotNull
    private PgType pgType; // 결제 수단 (예: NAVERPAY, KAKAO_PAY 등)

    @NotBlank
    private String paymentId; // 결제 ID (PG사에서 발급한 고유 ID)
}
