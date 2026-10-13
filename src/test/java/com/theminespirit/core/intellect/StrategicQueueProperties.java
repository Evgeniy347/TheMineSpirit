package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

class StrategicQueueProperties {
    @Provide
    Arbitrary<List<StrategicOrder>> orders() {
        return Arbitraries.integers().between(0, 5000)
                .flatMap(at -> Arbitraries.integers().between(1, 6000)
                        .map(ttl -> new StrategicOrder(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                "r-" + at + "-" + ttl, new ScriptCall("goto", Map.of("x", 1)), at, ttl)))
                .list().ofMinSize(1).ofMaxSize(6).map(list -> {
                    List<StrategicOrder> unique = new ArrayList<>();
                    for (StrategicOrder order : list) {
                        boolean seen = false;
                        for (StrategicOrder added : unique) {
                            if (added.requestId().equals(order.requestId())) {
                                seen = true;
                                break;
                            }
                        }
                        if (!seen) {
                            unique.add(order);
                        }
                    }
                    return unique;
                });
    }

    @Property
    void poll_EnqueuedOrders_FifoPerSpirit(@ForAll("orders") List<StrategicOrder> orders) {
        StrategicQueue queue = new StrategicQueue();
        for (StrategicOrder order : orders) {
            queue.enqueue(order);
        }
        List<String> polled = new ArrayList<>();
        while (true) {
            Optional<StrategicOrder> head =
                    queue.poll(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            if (head.isEmpty()) {
                break;
            }
            polled.add(head.orElseThrow().requestId());
        }

        List<String> expected = orders.stream()
                .sorted(Comparator.comparingLong(StrategicOrder::enqueuedAt)
                        .thenComparing(StrategicOrder::requestId))
                .map(StrategicOrder::requestId).toList();
        assertEquals(expected, polled,
                "Порядок выхода равен порядку входа: иначе дух перепрыгнет через приказ стратега");
    }

    @Property
    void prune_AfterPrune_NoExpiredLeft(@ForAll("orders") List<StrategicOrder> orders,
            @ForAll @LongRange(min = 0, max = 12000) long now) {
        StrategicQueue queue = new StrategicQueue();
        for (StrategicOrder order : orders) {
            queue.enqueue(order);
        }
        queue.prune(now);

        for (StrategicOrder left : queue.ordered()) {
            assertTrue(left.enqueuedAt() + left.ttlTicks() > now,
                    "После чистки просроченных нет: иначе мёртвый приказ дождётся исполнения");
        }
    }

    @Property
    void enqueue_SameSequence_Deterministic(@ForAll("orders") List<StrategicOrder> orders) {
        StrategicQueue first = new StrategicQueue();
        StrategicQueue second = new StrategicQueue();
        for (StrategicOrder order : orders) {
            first.enqueue(order);
            second.enqueue(order);
        }

        assertEquals(first.ordered(), second.ordered(),
                "Та же последовательность даёт ту же очередь: иначе приказы зависят от случая");
    }
}
