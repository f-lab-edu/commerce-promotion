package com.chae.promo.order.service;

import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.order.dto.OrderRequest;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderItem;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.entity.UserType;
import com.chae.promo.order.event.OrderPlacedEvent;
import com.chae.promo.order.event.OrderPlacedEventPublisher;
import com.chae.promo.order.mapper.OrderMapper;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.order.service.redis.StockRedisKeyManager;
import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.util.ProductValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final StockRedisKeyManager stockRedisKeyManager;
    private final StockRedisService stockRedisService;
    private final OrderPlacedEventPublisher orderPlacedEventPublisher;
    private final ProductValidator productValidator;
    private final OrderMapper orderMapper;

    @Transactional
    @Override
    public OrderResponse.Purchase placeOrder(OrderRequest.Purchase request, String userId) {

        //현재 비회원명 임시 사용
        UserType userType = UserType.GUEST;
        String ordererName = "비회원 " + userId;

        //상품 유효성 검증
        Map<String, Product> productMap = validateAndGetProductMap(request);

        //주문 생성 및 저장
        Order order = createAndSaveOrder(request, ordererName, productMap);
        orderRepository.flush();

        // 재고 예약
        String orderId = order.getPublicId();
        reserveStockInRedis(request.getItems(), orderId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //kafka 이벤트 발행
                publishOrderPlacedEvent(order, request, userId, userType);
            }
            @Override
            public void afterCompletion(int status) {
                if( status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    // 주문이 롤백되면 Redis 예약 취소
                    cancelStockInRedisQuietly(request.getItems(), orderId);
                }
            }
        });


        return orderMapper.toPurchaseResponse(order);
    }

    private Map<String, Product> validateAndGetProductMap(OrderRequest.Purchase request){
        // 요청된 상품 코드로 Product 엔티티를 한번에 조회하고, Map으로 변환
        try {
            return productValidator.getAndValidateProductMap(
                    request.getItems(),
                    OrderRequest.PurchaseItem::getProductCode,
                    OrderRequest.PurchaseItem::getQuantity
            );

        } catch (CommonCustomException e) {
            log.warn("상품 유효성 검증 실패: {}", e.getMessage());
            throw e;
        }
    }

    private Order createAndSaveOrder(OrderRequest.Purchase request, String ordererName, Map<String, Product> productMap){
        Order order = Order.builder()
                .customerId(null)
                .ordererName(ordererName)
                .publicId(UuidUtil.generate())
                .status(OrderStatus.PENDING_PAYMENT) // 초기 상태는 결제 대기
                .build();

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderRequest.PurchaseItem item : request.getItems()) {
            Product product = productMap.get(item.getProductCode());

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.addOrderItem(orderItem);

            //총 금액 계산 (단가 * 수량)
            totalPrice = totalPrice.add(
                    product.getPrice().multiply(new BigDecimal(item.getQuantity()))
            );
        }

        order.updateTotalPrice(totalPrice);

        return orderRepository.save(order);
    }

//    private void decreaseStockInRedis(List<OrderRequest.PurchaseItem> items){
//        for (OrderRequest.PurchaseItem item : items) {
//            String productStockKey = stockRedisKeyManager.getProductStockKey(item.getProductCode());
//            long requestedQuantity = item.getQuantity();
//
//            try {
//                // Redis Lua 스크립트를 통해 재고를 원자적으로 차감 시도
//                stockRedisService.decreaseStockAtomically(productStockKey, requestedQuantity);
//
//            } catch (CommonCustomException e) {
//                log.warn("Redis 재고 차감 실패 (비즈니스 예외). productCode:{}, quantity:{}, e:{}",
//                        item.getProductCode(), requestedQuantity, e.getMessage());
//                throw e;
//            } catch (RuntimeException e) {
//                log.error("Redis 재고 차감 중 RuntimeException 발생: {}", e.getMessage(), e);
//                throw e;
//            } catch (Exception e) {
//                log.error("Redis 재고 차감 중 예상치 못한 오류 발생. productCode:{}, e: {}",
//                        item.getProductCode(), e);
//                throw new RuntimeException("Redis 재고 차감 중 알 수 없는 오류가 발생했습니다.");
//            }
//        }
//    }

    private void reserveStockInRedis(List<OrderRequest.PurchaseItem> items, String orderId){
        long ttlSec = 60 * 10; // 10분 TTL
        for (OrderRequest.PurchaseItem item : items) {
            long requestedQuantity = item.getQuantity();

            try {
                // Redis Lua 스크립트를 통해 재고를 원자적으로 차감 시도
                stockRedisService.reserve(item.getProductCode(), orderId , requestedQuantity, ttlSec);

            } catch (CommonCustomException e) {
                log.warn("Redis 재고 예약 실패 (비즈니스 예외). productCode:{}, orderId: {}, quantity:{}, e:{}",
                        item.getProductCode(), orderId, requestedQuantity, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("Redis 재고 차감 중 예상치 못한 오류 발생. orderId:{}, e: {}",
                        orderId, e);
                throw new RuntimeException("Redis 재고 예약 중 알 수 없는 오류가 발생했습니다.");
            }
        }
    }

    private void publishOrderPlacedEvent(Order order, OrderRequest.Purchase request, String userId, UserType userType) {
        List<OrderPlacedEvent.Item> eventItems = request.getItems().stream()
                .map(item -> OrderPlacedEvent.Item.builder()
                        .productCode(item.getProductCode())
                        .decreasedStock(item.getQuantity())
                        .build())
                .toList();

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .eventId(UuidUtil.generate())
                .userId(userId)
                .userType(userType)
                .orderPublicId(order.getPublicId())
                .items(eventItems)
                .build();

        orderPlacedEventPublisher.publishOrderPlaced(event);
    }

    private void cancelStockInRedisQuietly(List<OrderRequest.PurchaseItem> items, String orderId) {
        for (var item : items) {
            try {
                stockRedisService.cancel(item.getProductCode(), orderId, item.getQuantity());
            } catch (Exception ex) {
                // 멱등/보상 성격이므로 조용히 로그만
                log.warn("Redis 예약 취소 중 오류(무시) productCode: {}, orderId: {}, err: {}",
                        item.getProductCode(), orderId, ex.toString());
            }
        }
    }
}
