package com.chae.promo.auth.domain;

import com.chae.promo.exception.AuthException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.security.resolver.AnonymousUserPrincipalResolver;
import com.chae.promo.security.resolver.UserPrincipalResolver;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuthProviderType {

    ANONYMOUS("anonymous", new AnonymousUserPrincipalResolver());

    private final String value;
    private final UserPrincipalResolver resolver;

    /**
     * 문자열 값을 통해 TokenType Enum을 찾아 반환
     *
     * @param value JWT Claims의 subject 값 (예: "anonymous", "refresh_token")
     * @return 해당 문자열 값에 대응하는 enum
     * @throws IllegalArgumentException 유효하지 않은 subject 값일 경우
     */
    public static AuthProviderType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("");
        }

        for (AuthProviderType type : AuthProviderType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new AuthException(CommonErrorCode.UNSUPPORTED_AUTH_PROVIDER);
    }

    // 자신의 전략(resolver)을 반환하는 메소드
    public UserPrincipalResolver getResolver() {
        return resolver;
    }
}
