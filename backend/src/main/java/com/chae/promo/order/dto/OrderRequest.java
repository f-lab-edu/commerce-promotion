package com.chae.promo.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;

public class OrderRequest {

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Purchase{
        @Valid
        private List<PurchaseItem> items; // 구매 상품 목록

        @AssertTrue(message = "상품 코드는 중복될 수 없습니다.")
        private boolean isProductCodesUnique() {
            if (items == null || items.isEmpty()) {
                return true;
            }
            return items.stream()
                    .map(PurchaseItem::getProductCode)
                    .distinct()
                    .count() == items.size();
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PurchaseItem{
        @NotBlank
        private String productCode; // 상품코드

        @Positive(message = "구매 수량은 1개 이상이어야 합니다.")
        private long quantity; //구매 수량

    }

}
