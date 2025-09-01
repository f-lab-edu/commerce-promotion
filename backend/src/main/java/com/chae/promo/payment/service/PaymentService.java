package com.chae.promo.payment.service;

import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.PaymentApprove;

public interface PaymentService {
    OrderResponse.OrderSummary start(String orderId);
    ApproveResult approve(PaymentApprove request, String userId);
}
