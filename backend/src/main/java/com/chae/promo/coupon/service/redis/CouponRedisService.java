package com.chae.promo.coupon.service.redis;

import com.chae.promo.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import com.chae.promo.exception.CommonCustomException;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRedisService {
    private final StringRedisTemplate stringRedisTemplate;


    // Redis 재고 차감 및 중복 발급 체크를 원자적으로 수행하는 Lua 스크립트
    // KEYS[1]: stockKey (재고 키)
    // KEYS[2]: userCouponKey (사용자-쿠폰 발급 상태 키)
    // ARGV[1]: userId (로깅용, Redis 명령에는 직접 사용 안 됨)
    // ARGV[2]: ttlMillis (사용자-쿠폰 발급 상태 키의 TTL, 밀리초 단위)
    // ARGV[3]: issueStatus (발급 상태 문자열, 예: "ISSUED")
    // 반환 값: 1 (성공), 2 (재고 소진), 3 (중복 발급), 0 (알 수 없는 오류)
    private static final String REDIS_ISSUE_COUPON_SCRIPT = """
            local couponStockKey = KEYS[1]
            local userCouponKey = KEYS[2]
            local userId = ARGV[1]
            local ttlSeconds = tonumber(ARGV[2])
            local issueStatus = ARGV[3]

            -- 1. 중복 발급 체크: 이미 발급된 쿠폰인지 확인
            if redis.call('EXISTS', userCouponKey) == 1 then
                return 3 -- 중복 발급
            end

            -- 2. 재고 차감: 현재 재고를 확인하고 0보다 클 경우에만 차감
            local currentStock = redis.call('GET', couponStockKey)
            if not currentStock then
                return  2-- 재고 키 없음 (재고 소진으로 간주)
            end
            currentStock = tonumber(currentStock)
            if currentStock <= 0 then
                return 2 -- 재고 소진
            end

            local updateStock = redis.call('DECRBY', couponStockKey, 1)

            -- 3. 발급 상태 저장: 재고 차감 성공 시에만 발급 상태 저장
            if updateStock >= 0 then
                redis.call('SET', userCouponKey, issueStatus)
                if ttlSeconds > 0 then
                    redis.call('EXPIRE', userCouponKey, math.floor(ttlSeconds))
                end
                return 1 -- 성공
            end

            return 0 -- 알 수 없는 오류
            """;
    private final DefaultRedisScript<Long> redisIssueCouponScript = new DefaultRedisScript<>(REDIS_ISSUE_COUPON_SCRIPT, Long.class);

    /**
     * Redis Lua 스크립트를 사용하여 쿠폰 재고를 차감하고 사용자에게 발급
     * 원자적으로 실행
     *
     * @param couponStockKey  쿠폰 재고를 관리하는 Redis 키
     * @param userCouponKey   사용자에게 쿠폰이 발급되었음을 표시하는 Redis 키
     * @param userId          사용자 ID (로깅 및 스크립트 ARGV 전달용)
     * @param ttlSeconds       userCouponKey의 만료 시간 (초)
     * @param issueStatus     발급 상태 문자열 (예: "ISSUED")
     * */
    public void issueCouponAtomically(String couponStockKey, String userCouponKey, String userId, long ttlSeconds, String issueStatus) {
        List<String> keys = Arrays.asList(couponStockKey, userCouponKey);
        String[] args = {userId, String.valueOf(ttlSeconds), issueStatus};

        Long result = stringRedisTemplate.execute(redisIssueCouponScript, keys, args);

        handleRedisScriptResult(result, userId, couponStockKey, userCouponKey);
    }

    /**
     * Lua 스크립트 실행 결과에 따라 적절한 예외를 처리합니다.
     *
     * @param result        Lua 스크립트 반환 값
     * @param userId        사용자 ID
     * @param couponStockKey      재고 키 (로깅용)
     * @param userCouponKey 사용자 쿠폰 발급 키 (로깅용)
     * @throws CommonCustomException 스크립트 결과에 따른 예외
     */
    private void handleRedisScriptResult(Long result, String userId, String couponStockKey, String userCouponKey) {
        if (result == null) {
            log.error("Redis 스크립트 실행 결과가 NULL입니다. user: {}, stockKey: {}, userCouponKey: {}", userId, couponStockKey, userCouponKey);
            throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);
        }

        switch (result.intValue()) {
            case 1 -> // 성공
                    log.info("Redis: 쿠폰 발급 성공. userCouponKey: {}", userCouponKey);
            case 2 -> { // 재고 소진
                log.info("Redis: 쿠폰 재고 소진. stockKey: {}, userId: {}", couponStockKey, userId);
                throw new CommonCustomException(CommonErrorCode.COUPON_SOLD_OUT);
            }
            case 3 -> { // 중복 발급
                log.info("Redis: 쿠폰 중복 발급. userCouponKey: {}", userCouponKey);
                throw new CommonCustomException(CommonErrorCode.COUPON_ALREADY_ISSUED);
            }
            default -> { // 기타 알 수 없는 오류
                log.error("Redis: 알 수 없는 오류 발생. user: {}, stockKey: {}, userCouponKey: {}, result: {}", userId, couponStockKey, userCouponKey, result);
                throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);
            }
        }
    }

    /**
     * Redis에 쿠폰 재고를 설정합니다.
     *
     * @param couponStockKey 쿠폰 재고 키
     * @param stock 재고 수량
     */
    public void setCouponStock(String couponStockKey, long stock) {
        stringRedisTemplate.opsForValue().set(couponStockKey, String.valueOf(stock));
        log.info("Redis: 쿠폰 재고 설정 완료. couponStockKey: {}, stock: {}", couponStockKey, stock);
    }

    /**
     * Redis에 쿠폰 재고 키가 존재하는지 확인합니다.
     *
     * @param couponStockKey 쿠폰 재고 키
     * @return 존재 여부
     */
    public Boolean hasCouponStockKey(String couponStockKey) {
        return stringRedisTemplate.hasKey(couponStockKey);
    }

    // 쿠폰 재고 복구 및 사용자 쿠폰 키 삭제를 원자적으로 수행하는 Lua 스크립트 정의
    // DB 저장 실패 시 Redis 상태를 롤백하는 데 사용
    // KEYS[1]: stockKey (재고 키 - 쿠폰 재고를 나타내는 Redis 키)
    // KEYS[2]: userCouponKey (사용자-쿠폰 발급 상태 키 - 특정 사용자가 특정 쿠폰을 발급받았음을 나타내는 Redis 키)
    // 반환 값: 1 (성공), 0 (오류 - 이 스크립트는 항상 1을 반환하도록 설계됨)
    private static final String ROLLBACK_SCRIPT = """
    redis.call('INCR', KEYS[1]) // KEYS[1]에 해당하는 쿠폰 재고를 1 증가 (차감된 재고 복구)
    redis.call('DEL', KEYS[2]) // KEYS[2]에 해당하는 사용자 쿠폰 발급 상태 키를 삭제
    return 1 // 스크립트 실행 성공을 나타내는 1을 반환
    """;
    private final DefaultRedisScript<Long> rollbackScript = new DefaultRedisScript<>(ROLLBACK_SCRIPT, Long.class);

    public void rollbackRedisCouponStock(String couponStockKey, String userCouponKey) {
        try {
            List<String> keys = Arrays.asList(couponStockKey, userCouponKey);
            stringRedisTemplate.execute(rollbackScript, keys);
            log.info("Redis 롤백 완료. stockKey: {}, userCouponKey: {}", couponStockKey, userCouponKey);
        } catch (Exception e) {
            log.error("Redis 롤백 실패. stockKey: {}, userCouponKey: {}", couponStockKey, userCouponKey, e);
        }
    }
}
