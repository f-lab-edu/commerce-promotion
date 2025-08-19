package com.chae.promo.order.event;

import com.chae.promo.order.entity.UserType;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.repository.ProductRepository;
import com.chae.promo.support.RetryTestAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


@SpringBootTest
@ActiveProfiles("dev")
@Import({RetryTestAspect.class})
class OrderPlacedHandlerServiceIT {

    @Autowired
    ProductRepository repo;
    @Autowired
    OrderPlacedHandlerService service;

    @Autowired
    private RetryTestAspect retryAspect;

    @Test
    void twoConcurrentTx_bothShouldSucceedWithRetry() throws Exception {
        retryAspect.reset();

        var p = repo.findAll().iterator().next();
        p.setStockQuantity(10);
        repo.save(p);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch done = new CountDownLatch(2);

        Runnable r = () -> {
            try {
                assertDoesNotThrow(() -> {
                    OrderPlacedEvent event = OrderPlacedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(UUID.randomUUID().toString())
                            .userType(UserType.GUEST)
                            .orderPublicId(UUID.randomUUID().toString())
                            .items(List.of(
                                    OrderPlacedEvent.Item.builder()
                                            .productCode(p.getCode())
                                            .decreasedStock(1)
                                            .build()
                            ))
                            .build();
                    service.processProductStockChange(event);
                });
            } finally {
                done.countDown();
            }
        };

        pool.submit(r);
        pool.submit(r);
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 둘 다 성공 → 재고는 8
        Product reloaded = repo.findById(p.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(8L);

        retryAspect.printStatistics();
        assertThat(retryAspect.getCallCountForMethod("processProductStockChange"))
                .isGreaterThan(2);

    }
}