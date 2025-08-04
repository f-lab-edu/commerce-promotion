package com.chae.promo.order.service.redis;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class StockRedisService {
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> redisDecreaseStockScript;

    public void decreaseStockAtomically(
            @NotBlank String productStockKey,
            @Positive long requestedCount
    ) {

        Long result = stringRedisTemplate.execute(
                redisDecreaseStockScript,
                Collections.singletonList(productStockKey),
                String.valueOf(requestedCount)
        );

        handleRedisScriptResult(result, productStockKey, requestedCount);
    }

    private void handleRedisScriptResult(Long result,
                                         String productStockKey,
                                         long requestedCount) {

        if (result == null) {
            log.error("Redis : null 반환. productStockKey: {}", productStockKey);
            throw new RuntimeException("Redis 장애");
        }

        switch (result.intValue()) {
            case -2 -> {// 재고키 없음
                log.info("Redis: 재고 키 없음. productStockKey: {}", productStockKey);
                throw new CommonCustomException(CommonErrorCode.PRODUCT_STOCK_NOT_FOUND);
            }
            case -1 -> { // 재고 부족
                log.info("Redis: 상품 재고 부족. productStockKey: {}, requestedCount: {}", productStockKey, requestedCount);
                throw new CommonCustomException(CommonErrorCode.PRODUCT_SOLD_OUT);
            }
            default ->  // 성공
                log.info("Redis: 상품 재고 차감 성공. productStockKey: {}, requestedCount: {}, remainingStock: {}", productStockKey, requestedCount, result);
        }
    }



}
