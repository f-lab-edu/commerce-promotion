package com.chae.promo.event.service;

import java.util.List;

public interface EventService {
    void scheduleEvent(String eventId, long delaySeconds);
    void markEventAsOpened(String eventId);
    boolean isAlreadyOpened(String eventId);
    List<String> getExpiredPendingEvents();
}
