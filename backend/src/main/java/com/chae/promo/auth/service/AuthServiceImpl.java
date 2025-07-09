package com.chae.promo.auth.service;

import com.chae.promo.common.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final JwtProvider jwtProvider;

    @Override
    public String createAnonymousToken() {
        return jwtProvider.generateAnonymousToken();
    }

    @Override
    public String getAnonIdFromToken(String token) {
        return jwtProvider.validateAndGetAnonId(token);
    }
}
