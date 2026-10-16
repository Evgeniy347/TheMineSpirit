package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardTuningTests {

    @Test
    void boardInterval_Matches_DemandSample() {
        assertEquals(100, BoardTuning.BOARD_INTERVAL_TICKS,
                "Пересборка доски идёт раз в 100 тиков — образец DEMAND_INTERVAL_TICKS из "
                        + "IntellectTuning: числа ядра держатся рядом, при смене интервала "
                        + "пересборки проверяют масштаб (п. 26)");
    }

    @Test
    void defaultTtl_Is_FiveMinutes() {
        assertEquals(6000, BoardTuning.DEFAULT_TTL_TICKS,
                "Срок жизни задания по умолчанию — 5 минут (6000 тиков): задание, которое "
                        + "взяли и не закончили, возвращается в очередь, а не висит вечно");
    }

    @Test
    void maxActivePerSpirit_Is_One() {
        assertEquals(1, BoardTuning.MAX_ACTIVE_PER_SPIRIT,
                "Активных заданий одного духа по умолчанию один: один исполнитель — один "
                        + "ремонт, остальные прикрывают существующим AssistPolicy (31)");
    }

    @Test
    void maxPending_Is_Reasonable() {
        assertEquals(64, BoardTuning.MAX_PENDING,
                "Предел очереди поселения — 64 задания: при превышении вытесняются самые "
                        + "старые невыданные, иначе очередь разрастается неограниченно");
    }

    @Test
    void craterTerritoryRadius_Is_OwnerDefault() {
        assertEquals(64, BoardTuning.CRATER_TERRITORY_RADIUS,
                "Воронка «за территорией» — дальше 64 блоков от дома группы (поправка 5 к "
                        + "чеклисту): взрыв внутри территории — зона работ координатора, "
                        + "задания доски не ставится");
    }

    @Test
    void herdScanInterval_Is_RareEnough() {
        assertEquals(200, BoardTuning.HERD_SCAN_INTERVAL_TICKS,
                "Опрос животных идёт раз в 200 тиков, а не каждый тик: scan_entities — "
                        + "дорогой запрос, каждый тик он грузил бы тик сервера");
    }

    @Test
    void craterBlocksEstimateRadius_Equals_CreeperPower() {
        assertEquals(3, BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS,
                "Радиус воронки крипера по умолчанию — 3 (сила взрыва ванильного крипера): "
                        + "оценка блоков засыпки по этому радиусу близка к реальной яме");
    }

    @Test
    void maxPriority_Bounds_TicketPriorities() {
        assertEquals(100, BoardTuning.MAX_PRIORITY,
                "Приоритеты заданий лежат в [0, 100]: граница проверяется конструктором "
                        + "BoardTicket, выход за неё означает, что доска не сможет "
                        + "сравнить задание с ремонтом");
    }

    @Property
    void anyNumber_Is_PositiveAndFinite(@ForAll("boardNumbers") Number number) {
        double value = number.doubleValue();

        assertTrue(value > 0, "Число доски должно быть положительным: " + number);
        assertTrue(Double.isFinite(value), "Число доски должно быть конечным: " + number);
    }

    @Provide
    Arbitrary<Number> boardNumbers() {
        return Arbitraries.of(
                BoardTuning.BOARD_INTERVAL_TICKS,
                BoardTuning.DEFAULT_TTL_TICKS,
                (long) BoardTuning.MAX_ACTIVE_PER_SPIRIT,
                (long) BoardTuning.MAX_PENDING,
                (long) BoardTuning.CRATER_TERRITORY_RADIUS,
                BoardTuning.HERD_SCAN_INTERVAL_TICKS,
                (long) BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS,
                (long) BoardTuning.MAX_PRIORITY);
    }
}
