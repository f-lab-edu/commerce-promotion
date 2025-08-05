package com.chae.promo.product.entity;

import com.chae.promo.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTime{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code; // 상품 관리 코드

    @Column(nullable = false)
    private String name; // 상품명

    @Column(nullable = false, precision = 13)
    private BigDecimal price; // 상품 가격

    @Column(nullable = false)
    private long stockQuantity; // 재고 수량

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status; // 상품 상태 (FOR_SALE, SOLD_OUT 등)

    @Version
    @Column(nullable = false)
    private Long version;
}