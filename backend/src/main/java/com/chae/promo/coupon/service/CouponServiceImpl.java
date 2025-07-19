package com.chae.promo.coupon.service;

import com.chae.promo.common.jwt.JwtUtil;
import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.coupon.dto.CouponRedisRequest;
import com.chae.promo.coupon.dto.CouponResponse;
import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import com.chae.promo.coupon.repository.CouponIssueRepository;
import com.chae.promo.coupon.repository.CouponRepository;
import com.chae.promo.coupon.service.redis.CouponRedisKeyManager;
import com.chae.promo.coupon.service.redis.CouponRedisService;
import com.chae.promo.coupon.util.CouponExpirationCalculator;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService{

    private final JwtUtil jwtUtil;
    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    private final CouponRedisService couponRedisService;
    private final CouponExpirationCalculator couponExpirationCalculator;
    private final CouponRedisKeyManager couponRedisKeyManager;

    @Transactional
    @Override
    public CouponResponse.Issue issueCoupon(String token) {
        //coupon id - 쿠폰 종류 1개만 있다고 가정
        String couponCode = "LABUBUISCOMMING";

        //토큰 검증 및 user id
        String userId = validateTokenAndExtractPrincipalId(token);

        // Redis Key 생성
        String couponStockKey = couponRedisKeyManager.getCouponStockKey(couponCode);
        String userCouponKey = couponRedisKeyManager.getUserCouponKey(userId, couponCode);
        String couponTtlKey = couponRedisKeyManager.getCouponTtlKey(couponCode);

        Coupon coupon = findCoupon(couponCode);

        // Redis에 재고가 없으면 DB에서 불러와 캐시
        if (Boolean.FALSE.equals(couponRedisService.hasCouponStockKey(couponStockKey))) {
            log.info("Redis에 쿠폰 재고 {}가 없습니다. DB에서 로드하여 캐시합니다.", couponStockKey);

            long initialStock = coupon.getTotalQuantity();
            couponRedisService.setCouponStock(couponStockKey, initialStock);
        }

        // 쿠폰 만료 시간 계산 및 TTL (초) 계산
        LocalDateTime calculatedExpireAt;
        long ttlSeconds;
        try {
            calculatedExpireAt = getCouponExpirationDateTime(coupon);
            ttlSeconds = couponExpirationCalculator.calculateTtlSeconds(calculatedExpireAt);

        } catch (CommonCustomException e) {
             log.warn("쿠폰 만료일이 지났습니다. 발급 중단. couponCode: {}", couponCode);
            throw e;
        }

        CouponRedisRequest couponRedisRequest = CouponRedisRequest.builder()
                .coupon(coupon)
                .couponStockKey(couponStockKey)
                .userCouponKey(userCouponKey)
                .couponTtlKey(couponTtlKey)
                .userId(userId)
                .couponIssueStatus(CouponIssueStatus.ISSUED)
                .ttlSeconds(ttlSeconds)
                .build();


        // Redis Lua 스크립트를 사용하여 원자적으로 쿠폰 확인, 재고 차감 및 발급 상태 저장
        couponRedisService.issueCouponAtomically(couponRedisRequest);

        //쿠폰 publicId 생성 (uuid)
        String publicId = UuidUtil.generate();

        //DB에 저장
        CouponIssue issue = saveCouponIssue(coupon, userId, calculatedExpireAt, couponStockKey, userCouponKey, publicId);


        return CouponResponse.Issue.builder()
                .couponIssueId(issue.getPublicId()) // publicId로 노출
                .code(coupon.getCode())
                .name(coupon.getName())
                .description(coupon.getDescription())
                .issuedAt(issue.getIssuedAt())
                .expireAt(issue.getExpireAt())
                .status(issue.getStatus().name())
                .build();

    }

    private LocalDateTime getCouponExpirationDateTime(Coupon coupon){
        return couponExpirationCalculator.calculateExpiration(coupon);
    }

    private String validateTokenAndExtractPrincipalId(String token){
        Claims claims = jwtUtil.validateToken(token);
        return claims.get("principalId", String.class);
    }

    private Coupon findCoupon(String couponCode){
        return couponRepository.findByCode(couponCode)
                .orElseThrow(() -> {
                    log.warn("쿠폰 조회 실패 couponCode: {}", couponCode);
                    return new CommonCustomException(CommonErrorCode.COUPON_NOT_FOUND);
                });
    }

    private CouponIssue saveCouponIssue(Coupon coupon,
                                        String userId,
                                        LocalDateTime expireAt,
                                        String stockKey,
                                        String userCouponKey,
                                        String publicId) {

        CouponIssue issue = CouponIssue.builder()
                .coupon(coupon)
                .userId(userId)
                .issuedAt(LocalDateTime.now())
                .expireAt(expireAt)
                .status(CouponIssueStatus.ISSUED)
                .publicId(publicId)
                .build();

        try {
            CouponIssue savedIssue = couponIssueRepository.save(issue);
            log.info("쿠폰 발급 DB 저장 완료. userId: {}, couponCode: {}, couponIssueId: {}",
                    userId, coupon.getCode(), savedIssue.getId());
            return savedIssue;

        } catch (Exception e) {
            log.error("쿠폰 발급 DB 저장 실패. userId: {}, couponCode: {}", userId, coupon.getCode(), e);
            couponRedisService.rollbackRedisCouponStock(stockKey, userCouponKey);

            throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);

        }


    }


}
