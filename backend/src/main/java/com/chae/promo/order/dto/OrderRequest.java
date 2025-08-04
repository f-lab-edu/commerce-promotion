package com.chae.promo.order.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

public class OrderRequest {

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Purchase{
        private List<PurchaseItem> items; // 구매 상품 목록
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PurchaseItem{
        private String productCode; // 상품코드
        private long quantity; //구매 수량
        private BigDecimal unitPrice; //단가
    }

}
