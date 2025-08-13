package com.chae.promo.payment.service;

import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.PaymentRequest;
import com.chae.promo.payment.dto.StartPaymentResponse;
import com.chae.promo.payment.entity.PgType;

public interface PaymentService {
    StartPaymentResponse start(String orderId, PgType pgType);
    ApproveResult approve(PaymentRequest request, String userId);
}
