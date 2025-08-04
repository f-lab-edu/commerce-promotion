package com.chae.promo.order.service;

import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderRequest;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderItem;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.order.service.redis.StockRedisKeyManager;
import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.entity.ProductStatus;
import com.chae.promo.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRedisKeyManager stockRedisKeyManager;
    private final StockRedisService stockRedisService;
    @Transactional
    @Override
    public OrderResponse.Purchase placeOrder(OrderRequest.Purchase request, String userId) {

        //현재 비회원명 임시 사용
        String ordererName = "비회원 " + userId;

        // 요청된 상품 코드로 Product 엔티티를 한번에 조회하고, Map으로 변환
        List<String> productCodes = request.getItems().stream()
                .map(OrderRequest.PurchaseItem::getProductCode)
                .toList();

        Map<String, Product> productMap = productRepository.findByCodeIn(productCodes)
                .stream()
                .collect(Collectors.toMap(Product::getCode, product -> product));

        // 2. 요청된 상품이 유효한지 먼저 확인
        for (OrderRequest.PurchaseItem item : request.getItems()) {
            if (!productMap.containsKey(item.getProductCode())) {
                log.warn("유효하지 않은 상품 코드 요청. code:{}", item.getProductCode());
                throw new CommonCustomException(CommonErrorCode.INVALID_PRODUCT_CODE);
            }
            Product product = productMap.get(item.getProductCode());
            if (product == null) {
                log.warn("존재하지 않는 상품 정보. code:{}", item.getProductCode());
                throw new CommonCustomException(CommonErrorCode.PRODUCT_NOT_FOUND);
            }
            if (product.getStatus() != ProductStatus.FOR_SALE) {
                log.warn("판매중이 아닌 상품. code:{}, status:{}", item.getProductCode(), product.getStatus());
                throw new CommonCustomException(CommonErrorCode.PRODUCT_NOT_FOR_SALE);
            }
        }


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
            totalPrice = totalPrice.add(
                    product.getPrice().multiply(new BigDecimal(item.getQuantity()))
            );
        }

        order.updateTotalPrice(totalPrice);

        orderRepository.save(order);

        //redis 재고 차감
        for (OrderRequest.PurchaseItem item : request.getItems()) {
            String productStockKey = stockRedisKeyManager.getProductStockKey(item.getProductCode());
            long requestedQuantity = item.getQuantity();

            try {
                // Redis Lua 스크립트를 통해 재고를 원자적으로 차감 시도
                stockRedisService.decreaseStockAtomically(productStockKey, requestedQuantity);

            } catch (CommonCustomException e) {
                log.warn("Redis 재고 차감 실패 (비즈니스 예외). productCode:{}, quantity:{}, e:{}",
                        item.getProductCode(), requestedQuantity, e.getMessage());
                throw e;
            } catch (RuntimeException e) {
                log.error("Redis 재고 차감 중 RuntimeException 발생: {}", e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                log.error("Redis 재고 차감 중 예상치 못한 오류 발생. productCode:{}, e: {}",
                        item.getProductCode(), e);
                throw new RuntimeException("Redis 재고 차감 중 알 수 없는 오류가 발생했습니다.");
            }
        }

        //kafka 주문 생성 이벤트 발행


        return OrderResponse.Purchase.builder()
                .customerId(order.getCustomerId())
                .publicId(order.getPublicId())
                .ordererName(order.getOrdererName())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus().name())
                .orderItems(
                        order.getOrderItems().stream()
                                .map(item -> OrderResponse.PurchaseItem.builder()
                                        .productCode(item.getProduct().getCode())
                                        .productName(item.getProduct().getName())
                                        .price(item.getUnitPrice())
                                        .quantity(item.getQuantity())
                                        .totalPrice(item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))
                                        .build())
                                .toList()
                )
                .build();
    }
}
