package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityQueueTests {

    private static ScriptCall task() {
        return new ScriptCall("patrol", Map.of("x", 1));
    }

    @Test
    void offer_SameKey_DoesNotDuplicate() {
        ActivityQueue queue = new ActivityQueue();

        assertTrue(
                queue.offer("night-guard", BoardCase.ACTIVITY_GUARD, "patrol", task(), 101, 100, 1200),
                "первый запрос ночной охраны встаёт в очередь");
        assertFalse(
                queue.offer("night-guard", BoardCase.ACTIVITY_GUARD, "patrol", task(), 101, 200, 1200),
                "тот же узел графа на следующем пересчёте только продлевает срок, а не плодит дубли");
        assertEquals(1, queue.size(), "в очереди одно занятие");
    }

    @Test
    void due_HigherWeight_StartsFirst() {
        ActivityQueue queue = new ActivityQueue();
        queue.offer("low", BoardCase.ACTIVITY_SOCIAL, "meet", task(), 20, 0, 1200);
        queue.offer("high", BoardCase.ACTIVITY_GUARD, "patrol", task(), 101, 0, 1200);

        assertEquals(
                List.of("high", "low"),
                queue.due(10).stream().map(ActivityQueue.Request::key).toList(),
                "сначала стартует занятие с большим весом графа, иначе ночная охрана ждёт уборку");
    }

    @Test
    void due_Expired_IsDropped() {
        ActivityQueue queue = new ActivityQueue();
        queue.offer("night-guard", BoardCase.ACTIVITY_GUARD, "patrol", task(), 101, 0, 1200);

        assertTrue(
                queue.due(1199).stream().anyMatch(request -> "night-guard".equals(request.key())),
                "до срока занятия запрос ещё в очереди");
        assertEquals(
                List.of(),
                queue.due(1200),
                "ночное занятие не стартует днём: истёкший запрос убирается, а не ждёт вечно");
    }
}
