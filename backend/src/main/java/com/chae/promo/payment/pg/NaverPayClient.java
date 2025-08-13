package com.chae.promo.payment.pg;

import com.chae.promo.payment.dto.NaverPayApproveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class NaverPayClient {
    private final WebClient client;
    @Value("${naverpay.partner-id}") String partnerId;
    @Value("${naverpay.client-id}") String clientId;
    @Value("${naverpay.client-secret}") String clientSecret;
    @Value("${naverpay.return-url}") String returnUrl;

    private Consumer<HttpHeaders> auth(String idem) {
        return h -> {
            h.add("X-Naver-Client-Id", clientId);
            h.add("X-Naver-Client-Secret", clientSecret);
            h.add("X-NaverPay-Idempotency-Key", idem); // 멱등키 권장
        };
    }

    // 1) 결제예약: reserveId 반환
    public String reserve(String orderKey, BigDecimal amount, String title, long quantity) {
        var req = Map.of(
                "modelVersion", "2",
                "merchantPayKey", orderKey,
                "merchantUserKey", "U-" + orderKey,
                "productName", title,
                "productCount", quantity,
                "totalPayAmount", amount,
                "returnUrl", returnUrl
        );
        Map<?,?> resp = client.post()
                .uri("/" + partnerId + "/naverpay/payments/v2/reserve")
                .headers(auth(UUID.randomUUID().toString()))
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        Map<?,?> body = (Map<?,?>) resp.get("body"); // 기본 응답 포맷 {code,message,body} 구조
        return (String) body.get("reserveId");
    }

    // 결제창 URL 구성
    public String buildPaymentPage(String reserveId, boolean prod) {
        String host = prod ? "https://m.pay.naver.com" : "https://test-m.pay.naver.com";
        return host + "/payments/" + reserveId;
    }

    // 3) 결제승인: paymentId로 승인
    public NaverPayApproveResponse approve(String paymentId) {
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();

        form.add("paymentId", paymentId);

        NaverPayApproveResponse response = client.post()
                .uri("/" + partnerId + "/naverpay/payments/v2/apply/payment")
                .headers(auth(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(NaverPayApproveResponse.class)
                .block();

        return response;
    }
}