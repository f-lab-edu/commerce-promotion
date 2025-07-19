package com.chae.promo.coupon.util;

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class CouponExpirationCalculator {
    public LocalDateTime calculateExpiration(Coupon coupon) {
        if (coupon.getExpireDate() != null) {
            return coupon.getExpireDate();
        }

        if (coupon.getValidDays() != null && coupon.getValidDays() > 0) {
            return LocalDateTime.now().plusDays(coupon.getValidDays());
        } else {
            log.warn("쿠폰 {}의 유효 일수 및 만료일이 설정되지 않았습니다. 기본 7일 유효 기간 적용.", coupon.getCode());
            return LocalDateTime.now().plusDays(7);
        }
    }

    public long calculateTtlSeconds(LocalDateTime expireAt) {
        Duration duration = Duration.between(LocalDateTime.now(), expireAt);
        long ttl = duration.getSeconds();
        if (ttl < 0) {
            log.warn("쿠폰 만료 TTL < 0. 쿠폰이 이미 만료되었습니다.");
            throw new CommonCustomException(CommonErrorCode.COUPON_EXPIRED);
        }
        if (ttl == 0) {
            log.info("쿠폰 만료 TTL이 0입니다. 최소 TTL 1초로 설정합니다.");
            return 1;
        }
        return ttl;
    }
}
