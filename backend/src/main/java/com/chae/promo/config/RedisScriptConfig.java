package com.chae.promo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    // [재고 차감] Redis 재고 차감 스크립트
    // KEYS[1]: productStockKey (상품 재고 키)
    // ARGV[1]: requestedCount (요청된 재고 차감 수량)
    private static final String REDIS_DECREASE_STOCK_SCRIPT = """
            local productStockKey = KEYS[1]
            local requestedCount = tonumber(ARGV[1])
            
            -- 1. 재고 차감: 현재 재고를 확인하고 0보다 클 경우에만 차감
            local currentStock = tonumber(redis.call('GET', productStockKey))
            
            -- 2. 재고 키가 존재하지 않으면 -1 반환
            if not currentStock then
                return -2 -- 재고 키가 존재하지 않음
            end
            
            -- 3. 현재 재고가 요청수량보다 많거나 같은지 확인
            if currentStock >= requestedCount then
                -- 4. 재고를 차감하고, 차감 후 남은 재고량을 반환 (성공)
                return redis.call('DECRBY', productStockKey, requestedCount)
            else
                -- 5. 재고가 부족한 경우 실패 처리
                return -1 -- 재고 부족
            end
            """;

    @Bean
    public DefaultRedisScript<Long> redisDecreaseStockScript (){
        return new DefaultRedisScript<>(REDIS_DECREASE_STOCK_SCRIPT, Long.class);
        }

}
