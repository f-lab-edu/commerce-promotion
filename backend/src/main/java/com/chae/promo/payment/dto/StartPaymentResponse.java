package com.chae.promo.payment.dto;

import com.chae.promo.payment.entity.PgType;

public record StartPaymentResponse(PgType pg, String reserveIdOrToken) {}
