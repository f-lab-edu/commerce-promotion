package com.chae.promo.auth.domain;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuthProviderType {

    ANONYMOUS("anonymous");

    private final String value;

    /**
     * 문자열 값을 통해 AuthProviderType Enum을 찾아 반환합니다.
     *
     * @param value JWT Claims의 subject 값 (예: "anonymous", "refresh_token")
     * @return 해당 문자열 값에 대응하는 Enum 상수
     * @throws CommonCustomException 입력된 value가 null이거나,
     * 해당하는 Enum 상수를 찾을 수 없을 경우 {@code CommonErrorCode.VALIDATION_FAILED}와 함께 발생
     */
    public static AuthProviderType fromValue(String value) {
        if (value == null) {
            throw new CommonCustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        for (AuthProviderType type : AuthProviderType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new CommonCustomException(CommonErrorCode.VALIDATION_FAILED);
    }

}
