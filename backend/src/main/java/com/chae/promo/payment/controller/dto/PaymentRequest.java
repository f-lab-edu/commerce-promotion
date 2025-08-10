package com.chae.promo.payment.controller.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentRequest {

    private String orderId; // 주문 ID
    private String paymentMethod; // 결제 수단 (예: 카드, 계좌이체 등)
    private String amount; // 결제 금액
    private String orderName; // 상품명


}
