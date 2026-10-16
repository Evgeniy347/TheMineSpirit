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
    void completedMemory_Is_Bounded() {
        assertEquals(1024, BoardTuning.COMPLETED_MEMORY,
                "Память завершённых заданий ограничена 1024: доска помнит выполненные ради "
                        + "зависимостей, но не растёт бесконечно от прогонов");
    }

    @Test
    void fenceWatchRadius_Is_LocalToSettlement() {
        assertEquals(32, BoardTuning.FENCE_WATCH_RADIUS,
                "Сломанный забор считается своим в 32 блоках от дома/духа: дальше него поломка "
                        + "природная, и доска не наряжает духа чинить чужой лес");
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

    @Test
    void liveNumbers_Match_TuningDefaults() {
        assertEquals(100, BoardTuning.boardIntervalTicks(),
                "Пересборка доски читается из живой таблицы: 100 тиков по умолчанию, "
                        + "иначе /reload интервал не поменяет");
        assertEquals(64, BoardTuning.maxPending(),
                "Предел очереди из живой таблицы — 64: иначе датапак не сможет поднять очередь");
        assertEquals(8, BoardTuning.stockReadBudget(),
                "Бюджет чтения склада — 8 контейнеров за пересборку: иначе снимок запасов "
                        + "читает склад целиком каждый интервал");
        assertEquals(4, BoardTuning.enchantShelvesMin(),
                "Полки дополняются при книгах меньше 4: иначе чаровальня ждёт полный ряд");
    }

    @Test
    void stockBlockBudget_Is_BoundedSample() {
        assertEquals(4096, BoardTuning.STOCK_BLOCK_BUDGET,
                "Снимок запасов читает не больше 4096 блоков за пересборку (образец ветки 32): "
                        + "без этого предела обход куба вокруг каждого духа грузил чанки на "
                        + "главном потоке — причина 41-минутного GameTest");
        assertEquals(4096, BoardTuning.stockBlockBudget(),
                "Бюджет блоков читается из живой таблицы: иначе /reload не изменил бы предел обхода");
    }

    @Test
    void failBackoff_Is_TenSeconds() {
        assertEquals(200, BoardTuning.FAIL_BACKOFF_TICKS,
                "Проваленное задание не переиздаётся 200 тиков (10 с): короче — доска ставила бы "
                        + "добычу каждую пересборку, длиннее — поселение надолго замирало бы");
        assertEquals(200, BoardTuning.failBackoffTicks(),
                "Откат провала читается из живой таблицы: датапак правит срок без перезапуска");
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
                (long) BoardTuning.MAX_PRIORITY,
                (long) BoardTuning.STOCK_READ_BUDGET,
                (long) BoardTuning.STOCK_BLOCK_BUDGET,
                BoardTuning.FAIL_BACKOFF_TICKS,
                (long) BoardTuning.COMPLETED_MEMORY,
                BoardTuning.FENCE_PATROL_INTERVAL,
                (long) BoardTuning.FENCE_WATCH_RADIUS,
                BoardTuning.ENCHANT_CHECK_INTERVAL,
                (long) BoardTuning.ENCHANT_SHELVES_MIN);
    }
}
