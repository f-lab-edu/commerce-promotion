package com.chae.promo.payment.controller;

import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.PaymentRequest;
import com.chae.promo.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "결제 api")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "결제 요청")
    public ResponseEntity<ApproveResult> requestPayment(
            @RequestBody @Valid PaymentRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        ApproveResult result = paymentService.approve(request, userDetails.getUsername());
        return ResponseEntity.ok(result);
    }

}
