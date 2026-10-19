package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.StrategicQueue.Outcome;
import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StrategicQueueTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static StrategicOrder order(String requestId, long enqueuedAt, long ttl) {
        return new StrategicOrder(SPIRIT, requestId, new ScriptCall("goto", Map.of("x", 1)), enqueuedAt, ttl);
    }

    @Test
    void poll_ThreeOrders_Fifo() {
        StrategicQueue queue = new StrategicQueue();
        queue.enqueue(order("r-1", 100, 6000));
        queue.enqueue(order("r-2", 200, 6000));
        queue.enqueue(order("r-3", 300, 6000));

        assertEquals(
                "r-1",
                queue.poll(SPIRIT).orElseThrow().requestId(),
                "Голова очереди — первое задание духа: стратег правит порядком постановки");
        assertEquals(
                "r-2",
                queue.poll(SPIRIT).orElseThrow().requestId(),
                "Второе задание ждёт первого: иначе дух перепрыгнет через приказ стратега");
        assertEquals(1, queue.size(), "Выданное задание покидает очередь: иначе дух исполнит его дважды");
    }

    @Test
    void enqueue_DuplicateRequestId_Rejected() {
        StrategicQueue queue = new StrategicQueue();
        queue.enqueue(order("r-1", 100, 6000));

        assertEquals(
                Outcome.DUPLICATE,
                queue.enqueue(order("r-1", 200, 6000)),
                "Дубль requestId не добавляется: иначе повтор стратега исполнится дважды");
        assertEquals(1, queue.size(), "Дубль не меняет очередь: иначе исполнение задвоится");
    }

    @Test
    void prune_OnlyExpired_Removed() {
        StrategicQueue queue = new StrategicQueue();
        queue.enqueue(order("old", 100, 500));
        queue.enqueue(order("fresh", 100, 6000));

        assertEquals(1, queue.prune(700), "Чистка снимает только просроченные: иначе свежий приказ умрёт раньше срока");
        assertEquals(
                Optional.empty(),
                queue.poll(UUID.fromString("00000000-0000-0000-0000-000000000000")),
                "Чужому духу голова не выдаётся: задания адресованы конкретным духам");
        assertEquals(
                "fresh",
                queue.poll(SPIRIT).orElseThrow().requestId(),
                "Свежее задание переживает чистку: иначе стратег потеряет приказ");
    }

    @Test
    void enqueue_StaleOrder_Expired() {
        StrategicQueue queue = new StrategicQueue();
        queue.prune(10000);

        assertEquals(
                Outcome.EXPIRED,
                queue.enqueue(order("late", 100, 500)),
                "Запоздалое задание не принимается: его срок вышел до постановки в очередь");
        assertEquals(0, queue.size(), "Просрочка не занимает очередь: иначе мёртвые приказы копятся");
    }

    @Test
    void order_BadInputs_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategicOrder(null, "r", new ScriptCall("goto", Map.of()), 0, 100),
                "Задание без духа не ставится: исполняет конкретный дух");
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategicOrder(SPIRIT, "  ", new ScriptCall("goto", Map.of()), 0, 100),
                "Задание без requestId не отсечёт дубли: повтор стратега исполнится дважды");
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategicOrder(SPIRIT, "r", new ScriptCall("goto", Map.of()), 0, 0),
                "Задание с нулевым сроком мёртво при постановке: срок обязан быть положительным");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StrategicQueue().poll(null),
                "Выдача без духа невозможна: задания адресованы конкретным духам");

        assertTrue(
                error.getMessage().contains("духа"),
                "Сообщение называет причину: при падении видно, что выдача требует адресата");
    }
}
