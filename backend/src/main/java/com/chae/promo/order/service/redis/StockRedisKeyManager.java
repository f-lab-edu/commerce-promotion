package com.chae.promo.order.service.redis;

import org.springframework.stereotype.Component;

@Component
public class StockRedisKeyManager {

    // 특정 상품의 재고 키 (자료구조: String)
    private static final String PRODUCT_STOCK_KEY = "product:stock:%s"; //  %s = productCode

    /**
     * 상품 재고 Key 생성
     * @param productCode 상품 코드
     * @return Redis 상품 재고 Key
     */
    public String getProductStockKey(String productCode) {
        return String.format(PRODUCT_STOCK_KEY, productCode);
    }
}

