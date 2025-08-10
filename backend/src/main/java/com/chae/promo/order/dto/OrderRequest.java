package com.chae.promo.order.dto;

import com.chae.promo.payment.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
        private List<PurchaseItem> items;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PurchaseItem{
        @NotBlank(message = "상품코드는 필수입니다.")
        private String productCode; // 상품코드

        @Positive(message = "구매 수량은 1개 이상이어야 합니다.")
        private long quantity; //구매 수량

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseItems {
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
    public static class Create{

        @Valid
        PurchaseItems purchaseItems;

        @Valid
        @NotNull(message = "배송지 정보는 필수입니다.")
        private ShippingInfo shippingInfo;

        private PaymentMethod paymentMethod;


    }


    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ShippingInfo {

        @NotBlank(message = "수령인 이름은 필수입니다.")
        private String recipientName;

        @NotBlank(message = "배송 주소는 필수입니다.")
        private String address;

        @NotBlank(message = "우편번호는 필수입니다.")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
        private String zipcode;

        @NotBlank(message = "연락처는 필수입니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        private String phoneNumber;

        private String memo;
    }


}
