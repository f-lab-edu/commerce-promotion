package com.chae.promo.order.service.redis;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class StockRedisService {
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> redisDecreaseStockScript;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> confirmScript;
    private final DefaultRedisScript<Long> cancelScript;
    private final StockRedisKeyManager key;


//    public void decreaseStockAtomically(
//            @NotBlank String productStockKey,
//            @Positive long requestedCount
//    ) {
//
//        Long result = stringRedisTemplate.execute(
//                redisDecreaseStockScript,
//                Collections.singletonList(productStockKey),
//                String.valueOf(requestedCount)
//        );
//
//        handleRedisScriptResult(result, productStockKey, requestedCount);
//    }
//
//    private void handleRedisScriptResult(Long result,
//                                         String productStockKey,
//                                         long requestedCount) {
//
//        if (result == null) {
//            log.error("Redis : null 반환. productStockKey: {}", productStockKey);
//            throw new RuntimeException("Redis 장애");
//        }
//
//        switch (result.intValue()) {
//            case -2 -> {// 재고키 없음
//                log.info("Redis: 재고 키 없음. productStockKey: {}", productStockKey);
//                throw new CommonCustomException(CommonErrorCode.PRODUCT_STOCK_NOT_FOUND);
//            }
//            case -1 -> { // 재고 부족
//                log.info("Redis: 상품 재고 부족. productStockKey: {}, requestedCount: {}", productStockKey, requestedCount);
//                throw new CommonCustomException(CommonErrorCode.PRODUCT_SOLD_OUT);
//            }
//            default ->  // 성공
//                log.info("Redis: 상품 재고 차감 성공. productStockKey: {}, requestedCount: {}, remainingStock: {}", productStockKey, requestedCount, result);
//        }
//    }
//


    public void reserve(String sku, String orderId, long quantity, long ttlSec) {
        String kAvail = key.available(sku);
        String kResv  = key.reserved(sku);
        String kHold  = key.hold(sku, orderId);
        String kIndex = key.holdIndex(sku);

        Long result = stringRedisTemplate.execute(
                reserveScript,
                List.of(kAvail, kResv, kHold, kIndex),
                String.valueOf(quantity),
                String.valueOf(ttlSec),
                String.valueOf(System.currentTimeMillis())
        );

        if (result == null) {
            log.error("Redis : null 반환. sku: {}", sku);
            throw new RuntimeException("Redis 장애");
        }

        switch (result.intValue()) {
            case 1 -> { // hold가 이미 존재 - 멱등. 성공처리
                log.info("Redis: 예약 성공 (또는 멱등). sku: {}, orderId: {}", sku, orderId);
            }
            case -2 -> {// 재고키 없음
                log.info("Redis: 재고 키 없음. sku: {}", sku);
                throw new CommonCustomException(CommonErrorCode.PRODUCT_STOCK_NOT_FOUND);
            }
            case -1 -> { // 재고 부족
                log.info("Redis: 상품 재고 부족. sku: {}, requestedCount: {}", sku, quantity);
                throw new CommonCustomException(CommonErrorCode.PRODUCT_SOLD_OUT);
            }
            default ->  {
                log.error("Redis: 예상치 못한 오류 code: {}, sku: {}, orderId: {}", result, sku, orderId);
                throw new IllegalStateException("Reserve unexpected: " + result);
            }
        }

    }

    /** 확정 (DB 커밋 후 afterCommit에서 호출 권장) */
    public void confirm(String sku, String orderId, long quantity) {

        if (quantity <= 0) {
            log.warn("확정할 수량이 0 이하입니다. sku: {}, orderId: {}, quantity: {}", sku, orderId, quantity);
            throw new CommonCustomException(CommonErrorCode.INVALID_QUANTITY);
        }

        String kAvail = key.available(sku);
        String kResv  = key.reserved(sku);
        String kHold  = key.hold(sku, orderId);
        String kIndex = key.holdIndex(sku);

        Long result = stringRedisTemplate.execute(
                confirmScript,
                List.of(kAvail, kResv, kHold, kIndex),
                String.valueOf(quantity)
        );
        if (result == null) {
            log.error("Redis : null 반환. sku: {}", sku);
            throw new RuntimeException("Redis 장애");
        }

        switch (result.intValue()){
            case 1 -> { // 확정 성공
                log.info("Redis: 예약 확정 성공. sku: {}, orderId: {}, quantity: {}", sku, orderId, quantity);
            }
            case -4 -> { // hold 없음(만료)
                log.info("Redis: hold 없음(만료). sku: {}, orderId: {}", sku, orderId);
                throw new CommonCustomException(CommonErrorCode.REDIS_STOCK_HOLD_MISSING_OR_EXPIRED);
            }
            case -3 -> { // 예약 부족
                log.info("Redis: 예약 개수 부족. sku: {}, orderId: {}, quantity: {}", sku, orderId, quantity);
                throw new IllegalStateException("예약 개수 부족");
            }
            default -> {
                log.error("Redis: 예상치 못한 오류 code: {}, sku: {}, orderId: {}", result, sku, orderId);
                throw new IllegalStateException("예상치 못한 오류 : " + result);
            }

        }
    }

    /** 취소 (DB 실패/롤백/만료 정리 등) */
    public void cancel(String sku, String orderId, long quantity) {
        if (quantity <= 0) return; // 멱등: 취소할 게 없으면 무시

        String kResv  = key.reserved(sku);
        String kHold  = key.hold(sku, orderId);
        String kIndex = key.holdIndex(sku);

        Long result = stringRedisTemplate.execute(
                cancelScript,
                List.of(kResv, kHold, kIndex),
                String.valueOf(quantity)
        );

        if (result == null) {
            log.error("Redis null on cancel, sku: {}, orderId: {}", sku, orderId);
            // afterRollback/정리잡에서도 호출될 수 있으니 예외 대신 경고 후 리턴 권장
            return;
        }
        if (result != 1L) {
            log.warn("Redis: 예상치 못한 취소 오류. code: {}, sku: {}, orderId: {}", result, sku, orderId);
        } else {
            log.info("Redis: 예약 취소 완료. sku:{}, orderId:{}, qty:{}", sku, orderId, quantity);
        }
    }
}
