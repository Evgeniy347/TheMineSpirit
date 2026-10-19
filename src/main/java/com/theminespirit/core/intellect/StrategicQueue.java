package com.theminespirit.core.intellect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StrategicQueue {
    public enum Outcome {
        ACCEPTED,
        DUPLICATE,
        EXPIRED
    }

    private final Map<String, StrategicOrder> byRequest = new LinkedHashMap<>();
    private long highWater;

    public Outcome enqueue(StrategicOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Задание не может быть null: стратег ставит конкретный план");
        }
        if (byRequest.containsKey(order.requestId())) {
            return Outcome.DUPLICATE;
        }
        if (order.enqueuedAt() + order.ttlTicks() <= highWater) {
            return Outcome.EXPIRED;
        }
        byRequest.put(order.requestId(), order);
        return Outcome.ACCEPTED;
    }

    public Optional<StrategicOrder> poll(UUID spirit) {
        if (spirit == null) {
            throw new IllegalArgumentException("Выдача без духа невозможна: задания адресованы конкретным духам");
        }
        StrategicOrder head = null;
        for (StrategicOrder order : byRequest.values()) {
            if (!order.spirit().equals(spirit)) {
                continue;
            }
            if (head == null
                    || order.enqueuedAt() < head.enqueuedAt()
                    || (order.enqueuedAt() == head.enqueuedAt()
                            && order.requestId().compareTo(head.requestId()) < 0)) {
                head = order;
            }
        }
        if (head == null) {
            return Optional.empty();
        }
        byRequest.remove(head.requestId());
        return Optional.of(head);
    }

    public int prune(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("Чистка в тике " + now + ": время не может быть отрицательным");
        }
        highWater = Math.max(highWater, now);
        List<String> expired = new ArrayList<>();
        for (StrategicOrder order : byRequest.values()) {
            if (order.enqueuedAt() + order.ttlTicks() <= now) {
                expired.add(order.requestId());
            }
        }
        for (String requestId : expired) {
            byRequest.remove(requestId);
        }
        return expired.size();
    }

    public int size() {
        return byRequest.size();
    }

    public List<StrategicOrder> ordered() {
        return byRequest.values().stream()
                .sorted(Comparator.comparingLong(StrategicOrder::enqueuedAt).thenComparing(StrategicOrder::requestId))
                .toList();
    }
}
