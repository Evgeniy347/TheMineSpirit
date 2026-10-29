package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplyGateTests {

    private static final long BACKOFF = BoardTuning.FAIL_BACKOFF_TICKS;

    @Test
    void needsDepositPoint_GatherMaterials_StartsWithoutContainer() {
        assertFalse(
                SupplyGate.needsDepositPoint(BoardCase.GATHER_MATERIALS),
                "Добыча не сдаёт сразу и умеет идти без точки сдачи: при пустом складе задание всё равно "
                        + "выходит на доску, а не ждёт сундук бесконечно");
    }

    @Test
    void needsDepositPoint_SupplySite_RequiresContainer() {
        assertTrue(
                SupplyGate.needsDepositPoint(BoardCase.SUPPLY_SITE),
                "Снабжение со склада без контейнера сдавать некуда: задание не ставится, доска ставит сундук");
    }

    @Test
    void decide_StockInsideMinimum_GathersInsteadOfSupply() {
        SupplyGate.Decision decision =
                SupplyGate.decide(Map.of("dirt", 59), Map.of("dirt", 64), 64, Map.of(), 0, BACKOFF);

        assertTrue(
                decision.supply().isEmpty(),
                "Снабжение со склада не ставится, когда весь запас держится минимумом: дух шёл бы к сундуку, "
                        + "брал 0 и закрывал задание без доставки, а нехватка оставалась бы");
        assertEquals(
                List.of(new SupplyGate.Request("dirt", 59)),
                decision.gather(),
                "Нехватку сверх неприкосновенного минимума закрывает добыча, а не снабжение со склада");
    }

    @Test
    void decide_StockAboveMinimum_SuppliesFromStock() {
        SupplyGate.Decision decision =
                SupplyGate.decide(Map.of("cobblestone", 141), Map.of("cobblestone", 160), 64, Map.of(), 0, BACKOFF);

        assertEquals(
                List.of(new SupplyGate.Request("cobblestone", 141)),
                decision.supply(),
                "Материал выше минимума берётся со склада: снабжение разрешено");
        assertTrue(decision.gather().isEmpty(), "Добыча не нужна, пока склад отдаёт материал");
    }

    @Test
    void decide_RecentlyClosedSupply_WaitsForBackoff() {
        Map<String, Long> closedAt = Map.of("supply:cobblestone", 100L);

        SupplyGate.Decision waiting =
                SupplyGate.decide(Map.of("cobblestone", 141), Map.of("cobblestone", 96), 64, closedAt, 150, BACKOFF);
        assertTrue(
                waiting.isEmpty(),
                "Снабжение, только что закрытое без доставки, не пересоздаётся сразу: доска не выдаёт "
                        + "его каждую пересборку");

        SupplyGate.Decision after = SupplyGate.decide(
                Map.of("cobblestone", 141), Map.of("cobblestone", 96), 64, closedAt, 100 + BACKOFF, BACKOFF);
        assertEquals(
                List.of(new SupplyGate.Request("cobblestone", 141)),
                after.supply(),
                "После отката снабжение ставится снова: пауза не запрещает задание навсегда");
    }

    @Test
    void decide_GatherHeldByMinimum_StillGathers() {
        SupplyGate.Decision decision =
                SupplyGate.decide(Map.of("dirt", 59), Map.of("dirt", 64), 64, Map.of("supply:dirt", 0L), 1, BACKOFF);

        assertEquals(
                List.of(new SupplyGate.Request("dirt", 59)),
                decision.gather(),
                "Минимум не даёт взять материал со склада: добычу нельзя откладывать из-за отката снабжения");
    }
}
