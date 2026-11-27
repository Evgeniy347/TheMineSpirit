package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.ActivityTickets;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardActivityDeskTests {
    @Test
    void idleSense_LocalFallback_HasOwnerAndLowPriority() {
        UUID spirit = UUID.fromString("11111111-2222-3333-4444-555555555555");
        BlockCoord at = new BlockCoord(10, 64, -5);

        ActivityTickets.Request request = BoardActivityDesk.idleSense(spirit, at, 100);

        assertEquals(
                BoardCase.ACTIVITY_SENSE,
                request.identity().activity(),
                "Fallback использует штатный локальный scan: иначе занятие не проходит каталог и дух снова простаивает. При смене idleSense_LocalFallback_HasOwnerAndLowPriority проверь MoreActivityPlans.plan.");
        assertEquals(
                Optional.of(spirit),
                request.crew().leader(),
                "Fallback адресован конкретному свободному духу: иначе один scan забирает только первого, а остальные ждут. При смене idleSense_LocalFallback_HasOwnerAndLowPriority проверь BoardActivityDesk.fillIdle.");
        assertEquals(
                BoardActivityDesk.FALLBACK_PRIORITY,
                request.priority(),
                "Fallback имеет низкий приоритет: иначе scan вытесняет еду, сад, склад и расчистку вместо ожидания стратегической работы. При смене idleSense_LocalFallback_HasOwnerAndLowPriority проверь DispatchRound.preemption.");
        assertEquals(
                "scan_blocks",
                request.plan().script(),
                "Fallback выполняет локальное сканирование без goto: иначе путь к общему дому снова блокирует свободного духа. При смене idleSense_LocalFallback_HasOwnerAndLowPriority проверь BoardActivityDesk.idleSense.");
    }
}
