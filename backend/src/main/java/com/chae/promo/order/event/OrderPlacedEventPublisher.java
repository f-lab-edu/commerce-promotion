package com.chae.promo.order.event;

import com.chae.promo.common.kafka.TopicNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        kafkaTemplate.send(TopicNames.ORDER_PLACED, event.getOrderPublicId(), event);
        log.info("주문 완료 이벤트 발행. orderPublicId: {}, userId: {}", event.getOrderPublicId(), event.getUserId());
    }
}