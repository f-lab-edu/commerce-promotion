package com.chae.promo.outbox.service;

import com.chae.promo.outbox.entity.EventOutbox;
import com.chae.promo.outbox.repository.EventOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final ObjectMapper objectMapper;

    private final EventOutboxRepository eventOutboxRepository;

    @Transactional
    @Override
    public void saveEvent(String eventId, String type, String aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            Instant now = Instant.now();

            EventOutbox eventOutbox = EventOutbox.builder()
                    .eventId(eventId)
                    .type(type)
                    .aggregateId(aggregateId)
                    .payloadJson(json)
                    .status(EventOutbox.Status.PENDING)
                    .retryCount(0)
                    .nextRetryAt(now)
                    .build();

            eventOutboxRepository.save(eventOutbox);
        } catch (Exception e) {
            throw new RuntimeException("Outbox 저장 실패", e);
        }
    }

}
