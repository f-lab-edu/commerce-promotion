package com.chae.promo.payment.service;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.payment.dto.*;
import com.chae.promo.payment.entity.Payment;
import com.chae.promo.payment.entity.PaymentMethod;
import com.chae.promo.payment.entity.PaymentStatus;
import com.chae.promo.payment.pg.NaverPayClient;
import com.chae.promo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final NaverPayClient naver;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    @Override
    public OrderResponse.OrderSummary prepare(PaymentPrepareRequest request, String userId) {
        Order order = orderRepository.findByPublicId(request.orderId())
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        validateOrderForPayment(order, userId);

        validatePaymentMethod(request.payments());

        validatePaymentAmount(order, request.payments());

        order.markPendingPayment();

        createAndSavePayments(order, request);

        return OrderResponse.OrderSummary.builder()
                .publicId(order.getPublicId())
                .itemCount(order.getOrderItems().size())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus().name())
                .build();

    }

    private void validateOrderForPayment(Order order, String userId){
        //결제 가능한 상태인지 검증
        if (order.getStatus() != OrderStatus.CREATED) {
            log.warn("결제 불가능한 상태: {}, 주문 ID: {}", order.getStatus(), order.getPublicId());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);
        }

        validateOrdererForPayment(order, userId);
    }

    //주문자와 결제자가 일치하는지 검증
    private void validateOrdererForPayment(Order order, String userId){
        if (order.getCustomerId() == null) {
            if (!order.getOrdererName().contains(userId)) {
                log.warn("비회원 주문자와 결제자가 일치하지 않습니다. 주문자: {}, 결제자: {}", order.getOrdererName(), userId);
                throw new CommonCustomException(CommonErrorCode.NOT_ALLOWED);
            }
        } else {
            if (!String.valueOf(order.getCustomerId()).equals(userId)) {
                log.warn("주문자와 결제자가 일치하지 않습니다. 주문자: {}, 결제자: {}", order.getCustomerId(), userId);
                throw new CommonCustomException(CommonErrorCode.NOT_ALLOWED);
            }
        }
    }

    //결제 금액이 주문 금액과 일치하는지 검증
    private void validatePaymentAmount(Order order, List<PaymentMethodRequest> request){
        BigDecimal totalRequestAmount = request.stream()
                .map(PaymentMethodRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if(order.getTotalPrice().compareTo(totalRequestAmount) != 0) {
            log.warn("결제 금액 불일치. 주문 금액: {}, 요청 금액: {}", order.getTotalPrice(), totalRequestAmount);
            throw new CommonCustomException(CommonErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

    }

    //중복된 결제 수단이 있는지 검증
    private void validatePaymentMethod(List<PaymentMethodRequest> request){
        Set<PaymentMethod> methods = new HashSet<>();
        for (PaymentMethodRequest req : request) {
            if (!methods.add(req.method())) {
                throw new CommonCustomException(CommonErrorCode.DUPLICATED_PAYMENT_METHOD);
            }
        }
    }

    //결제 정보 생성 및 저장
    private void createAndSavePayments(Order order, PaymentPrepareRequest request){

        List<Payment> payments = request.payments().stream()
                .map(paymentMethod -> Payment.builder()
                        .order(order)
                        .paymentMethod(paymentMethod.method())
                        .amount(paymentMethod.amount())
                        .status(PaymentStatus.PENDING)
                        .build()
                ).toList();

        paymentRepository.saveAll(payments);
    }

    @Transactional
    public ApproveResult approve(PaymentApprove request, String userId) {
        String paymentId = request.getPaymentId();

        Order order = orderRepository.findByPublicId(request.getOrderId())
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        //주문자 검증
        validateOrdererForPayment(order, userId);
        //멱등 처리
        if (order.getStatus() == OrderStatus.PAID) {
            return new ApproveResult(paymentId, order.getTotalPrice());
        }

        // 결제 가능한 상태인지 검증
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {

            log.warn("결제 불가능한 상태: {}, 주문 ID: {}", order.getStatus(), order.getPublicId());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);
        }

        LocalDateTime paidAt;
        PaymentMethod paymentMethod;

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
                BigDecimal paidAmount = BigDecimal.valueOf(detail.totalPayAmount()); //long -> BigDecimal
                if (paidAmount.compareTo(order.getTotalPrice()) != 0) {
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

        //payment 저장 todo. payment 상태변경 로직 추가
//        Payment payment = Payment.builder()
//                .order(order)
//                .paymentKey(paymentId)
//                .paymentMethod(paymentMethod)
//                .amount(order.getTotalPrice())
//                .status(PaymentStatus.COMPLETED)
//                .paidAt(paidAt)
//                        .build();
//
//        paymentRepository.save(payment);


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