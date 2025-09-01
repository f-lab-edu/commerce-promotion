package com.chae.promo.payment.service;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.repository.OrderItemRepository;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.NaverPayApproveResponse;
import com.chae.promo.payment.dto.PaymentApprove;
import com.chae.promo.payment.entity.Payment;
import com.chae.promo.payment.entity.PaymentMethod;
import com.chae.promo.payment.entity.PaymentStatus;
import com.chae.promo.payment.pg.NaverPayClient;
import com.chae.promo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final NaverPayClient naver;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public OrderResponse.OrderSummary start(String orderId) {
        Order order = orderRepository.findByPublicId(orderId)
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        if(order.getStatus() != OrderStatus.CREATED){
            log.warn("결제 불가능한 상태: {}, 주문 ID: {}", order.getStatus(), order.getPublicId());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);
        }

        order.markPendingPayment();

        return OrderResponse.OrderSummary.builder()
                .publicId(order.getPublicId())
                .itemCount(order.getOrderItems().size())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus().name())
                .build();

    }

    @Transactional
    public ApproveResult approve(PaymentApprove request, String userId) {
        String paymentId = request.getPaymentId();

        Order order = orderRepository.findByPublicId(request.getOrderId())
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        //주문자 검증
        if(order.getCustomerId() == null){
            if (!order.getOrdererName().contains(userId)) {
                log.warn("비회원 주문자와 결제자가 일치하지 않습니다. 주문자: {}, 결제자: {}", order.getOrdererName(), userId);
                throw new CommonCustomException(CommonErrorCode.NOT_ALLOWED);
            }
        }else {
            if (!order.getCustomerId().equals(userId)) {
                log.warn("주문자와 결제자가 일치하지 않습니다. 주문자: {}, 결제자: {}", order.getCustomerId(), userId);
                throw new CommonCustomException(CommonErrorCode.NOT_ALLOWED);
            }
        }

        //멱등 처리
        if (order.getStatus() == OrderStatus.PAID) {
            return new ApproveResult(paymentId, order.getTotalPrice());
        }

        // 결제 가능한 상태인지 검증
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {

            log.warn("결제 불가능한 상태: {}, 주문 ID: {}", order.getStatus(), order.getPublicId());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);
        }

        LocalDateTime paidAt = null;
        PaymentMethod paymentMethod = null;

        switch (request.getPgType()) {
            case NAVERPAY -> {

                NaverPayApproveResponse response = naver.approve(paymentId);

                var detail = response.body().detail();

                //승인 실패
                if (!"SUCCESS".equalsIgnoreCase(detail.admissionState())) {
                    log.warn("결제 승인 실패: {}", detail.admissionState());
                    throw new CommonCustomException(
                            CommonErrorCode.PAYMENT_APPROVAL_FAILED
                    );
                }

                // 금액 검증
                long paidAmount = detail.totalPayAmount();
                if (paidAmount != order.getTotalPrice().longValueExact()) {
                    log.warn("결제 금액 불일치. 주문 금액 = {}, 결제 금액 = {}", order.getTotalPrice(), paidAmount);
                    throw new CommonCustomException(CommonErrorCode.PAYMENT_AMOUNT_MISMATCH);
                }

                // 주문 ID 검증
                if (!request.getOrderId().equals(detail.merchantPayKey())) {
                    throw new CommonCustomException(
                            CommonErrorCode.PAYMENT_ORDER_MISMATCH
                    );
                }

                paidAt = parseNaverYmdt(detail.admissionYmdt());
                paymentMethod = PaymentMethod.fromValue(detail.primaryPayMeans());
            }

            default -> throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_SUPPORTED_PG_TYPE);
        }


        // 도메인 상태 변경
        order.markPaid();

        //payment 저장
        Payment payment = Payment.builder()
                .order(order)
                .paymentKey(paymentId)
                .paymentMethod(paymentMethod)
                .amount(order.getTotalPrice())
                .status(PaymentStatus.COMPLETED)
                .paidAt(paidAt)
                        .build();

        paymentRepository.save(payment);


        ApproveResult result = new ApproveResult(paymentId, order.getTotalPrice());

        return result;
    }

    private static LocalDateTime parseNaverYmdt(String ymdt) {
        DateTimeFormatter NAVER_YMDT =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        if (ymdt == null || ymdt.length() != 14 || !ymdt.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid ymdt: " + ymdt);
        }
        try {
            return LocalDateTime.parse(ymdt, NAVER_YMDT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Failed to parse ymdt: " + ymdt, e);
        }
    }
}