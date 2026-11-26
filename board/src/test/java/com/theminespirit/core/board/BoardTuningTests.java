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
        assertEquals(
                100,
                BoardTuning.BOARD_INTERVAL_TICKS,
                "Пересборка доски идёт раз в 100 тиков — образец DEMAND_INTERVAL_TICKS из "
                        + "IntellectTuning: числа ядра держатся рядом, при смене интервала "
                        + "пересборки проверяют масштаб (п. 26)"
                        + " Иначе тест boardInterval_Matches_DemandSample перестанет ловить эту поломку.");
    }

    @Test
    void maxActivePerSpirit_Is_One() {
        assertEquals(
                1,
                BoardTuning.MAX_ACTIVE_PER_SPIRIT,
                "Активных заданий одного духа по умолчанию один: один исполнитель — один "
                        + "ремонт, остальные прикрывают существующим AssistPolicy (31)"
                        + " Иначе тест maxActivePerSpirit_Is_One перестанет ловить эту поломку. При смене maxActivePerSpirit_Is_One проверь это утверждение.");
    }

    @Test
    void maxPending_Is_Reasonable() {
        assertEquals(
                64,
                BoardTuning.MAX_PENDING,
                "Предел очереди поселения — 64 задания: при превышении вытесняются самые "
                        + "старые невыданные, иначе очередь разрастается неограниченно"
                        + " При смене maxPending_Is_Reasonable проверь это утверждение.");
    }

    @Test
    void chopRadius_FromData_IsOwnerHundred() {
        assertEquals(
                100,
                BoardTuning.chopRadius(),
                "Радиус поиска деревьев для рубки — 100 блоков и для фона, и для доски (решение владельца 26.10, "
                        + "INC-83: фон рубил в 16 и «деревья закончились»); число — в board.json, ключ chopRadius"
                        + " Иначе тест chopRadius_FromData_IsOwnerHundred перестанет ловить эту поломку. При смене chopRadius_FromData_IsOwnerHundred проверь это утверждение.");
    }

    @Test
    void craterTerritoryRadius_Is_OwnerDefault() {
        assertEquals(
                64,
                BoardTuning.CRATER_TERRITORY_RADIUS,
                "Воронка «за территорией» — дальше 64 блоков от дома группы (поправка 5 к "
                        + "чеклисту): взрыв внутри территории — зона работ координатора, "
                        + "задания доски не ставится"
                        + " Иначе тест craterTerritoryRadius_Is_OwnerDefault перестанет ловить эту поломку. При смене craterTerritoryRadius_Is_OwnerDefault проверь это утверждение.");
    }

    @Test
    void fenceWatchRadius_Is_LocalToSettlement() {
        assertEquals(
                32,
                BoardTuning.FENCE_WATCH_RADIUS,
                "Сломанный забор считается своим в 32 блоках от дома/духа: дальше него поломка "
                        + "природная, и доска не наряжает духа чинить чужой лес"
                        + " Иначе тест fenceWatchRadius_Is_LocalToSettlement перестанет ловить эту поломку. При смене fenceWatchRadius_Is_LocalToSettlement проверь это утверждение.");
    }

    @Test
    void craterBlocksEstimateRadius_Equals_CreeperPower() {
        assertEquals(
                3,
                BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS,
                "Радиус воронки крипера по умолчанию — 3 (сила взрыва ванильного крипера): "
                        + "оценка блоков засыпки по этому радиусу близка к реальной яме"
                        + " Иначе тест craterBlocksEstimateRadius_Equals_CreeperPower перестанет ловить эту поломку. При смене craterBlocksEstimateRadius_Equals_CreeperPower проверь это утверждение.");
    }

    @Test
    void maxPriority_Bounds_TicketPriorities() {
        assertEquals(
                100,
                BoardTuning.MAX_PRIORITY,
                "Приоритеты заданий лежат в [0, 100]: граница проверяется конструктором "
                        + "BoardTicket, выход за неё означает, что доска не сможет "
                        + "сравнить задание с ремонтом"
                        + " При смене maxPriority_Bounds_TicketPriorities проверь это утверждение.");
    }

    @Test
    void liveNumbers_Match_TuningDefaults() {
        assertEquals(
                100,
                BoardTuning.boardIntervalTicks(),
                "Пересборка доски читается из живой таблицы: 100 тиков по умолчанию, "
                        + "иначе /reload интервал не поменяет"
                        + " При смене liveNumbers_Match_TuningDefaults проверь это утверждение.");
        assertEquals(
                64,
                BoardTuning.maxPending(),
                "Предел очереди из живой таблицы — 64: иначе датапак не сможет поднять очередь"
                        + " При смене liveNumbers_Match_TuningDefaults проверь это утверждение.");
        assertEquals(
                8,
                BoardTuning.stockReadBudget(),
                "Бюджет чтения склада — 8 контейнеров за пересборку: иначе снимок запасов "
                        + "читает склад целиком каждый интервал"
                        + " При смене liveNumbers_Match_TuningDefaults проверь это утверждение.");
        assertEquals(
                20,
                BoardTuning.dispatchRetryTicks(),
                "Повтор раздачи после освобождения духа — раз в 20 тиков: живой замер 16.11 показал 743 раздачи "
                        + "за окно по ~57 заявок, choose держал тик; интервал живёт в таблице, /reload его меняет"
                        + " Иначе тест liveNumbers_Match_TuningDefaults перестанет ловить эту поломку. При смене liveNumbers_Match_TuningDefaults проверь это утверждение.");
    }

    @Test
    void stockBlockBudget_Is_BoundedSample() {
        assertEquals(
                512,
                BoardTuning.STOCK_BLOCK_BUDGET,
                "Снимок запасов читает не больше 512 блоков за пересборку: иначе стартовый обход 4096 блоков "
                        + "занимал 2461 мс и держал первый тик сервера. Полный радиус теперь проходит "
                        + "возобновляемыми срезами StockScan.scan. При смене stockBlockBudget_Is_BoundedSample "
                        + "проверь StockReader.scan, StockScanTests и TickWatch после запуска.");
        assertEquals(
                512,
                BoardTuning.stockBlockBudget(),
                "Бюджет блоков читается из живой таблицы: иначе /reload не изменил бы предел обхода"
                        + " При смене stockBlockBudget_Is_BoundedSample проверь это утверждение.");
    }

    @Test
    void failBackoff_Is_TenSeconds() {
        assertEquals(
                200,
                BoardTuning.FAIL_BACKOFF_TICKS,
                "Проваленное задание не переиздаётся 200 тиков (10 с): короче — доска ставила бы "
                        + "добычу каждую пересборку, длиннее — поселение надолго замирало бы"
                        + " Иначе тест failBackoff_Is_TenSeconds перестанет ловить эту поломку. При смене failBackoff_Is_TenSeconds проверь это утверждение.");
        assertEquals(
                200,
                BoardTuning.failBackoffTicks(),
                "Откат провала читается из живой таблицы: датапак правит срок без перезапуска"
                        + " Иначе тест failBackoff_Is_TenSeconds перестанет ловить эту поломку. При смене failBackoff_Is_TenSeconds проверь это утверждение.");
    }

    @Test
    void caseFailPause_Is_OneMinute() {
        assertEquals(
                1200,
                BoardTuning.CASE_FAIL_PAUSE_TICKS,
                "После провала пара дух+кейс отдыхает 1200 тиков (1 мин): иначе дух 112 раз подряд "
                        + "берёт first:stock_chests и стоит на месте"
                        + " При смене caseFailPause_Is_OneMinute проверь это утверждение.");
        assertEquals(
                1200,
                BoardTuning.caseFailPauseTicks(),
                "Пауза пары дух+кейс читается из живой таблицы board: датапак меняет её без перезапуска"
                        + " Иначе тест caseFailPause_Is_OneMinute перестанет ловить эту поломку. При смене caseFailPause_Is_OneMinute проверь это утверждение.");
    }

    @Test
    void caseFailMultiplier_Is_Two() {
        assertEquals(
                2,
                BoardTuning.CASE_FAIL_MULTIPLIER,
                "Каждый следующий провал удваивает паузу пары дух+кейс: без роста дух возвращается "
                        + "к сбойному заданию с той же частотой"
                        + " Иначе тест caseFailMultiplier_Is_Two перестанет ловить эту поломку. При смене caseFailMultiplier_Is_Two проверь это утверждение.");
        assertEquals(
                2,
                BoardTuning.caseFailMultiplier(),
                "Множитель паузы читается из живой таблицы board: датапак меняет его без перезапуска"
                        + " Иначе тест caseFailMultiplier_Is_Two перестанет ловить эту поломку. При смене caseFailMultiplier_Is_Two проверь это утверждение.");
    }

    @Test
    void caseFailPauseCap_Is_EightMinutes() {
        assertEquals(
                9600,
                BoardTuning.CASE_FAIL_PAUSE_CAP_TICKS,
                "Потолок паузы — 9600 тиков (8 мин): без него экспонента выключала бы духа навсегда"
                        + " Иначе тест caseFailPauseCap_Is_EightMinutes перестанет ловить эту поломку. При смене caseFailPauseCap_Is_EightMinutes проверь это утверждение.");
        assertEquals(
                9600,
                BoardTuning.caseFailPauseCapTicks(),
                "Потолок паузы читается из живой таблицы board: иначе /reload не ограничил бы откат"
                        + " При смене caseFailPauseCap_Is_EightMinutes проверь это утверждение.");
    }

    @Test
    void ticketFailLimit_Is_Five() {
        assertEquals(
                5,
                BoardTuning.TICKET_FAIL_LIMIT,
                "Пять провалов подряд блокируют само задание: иначе одна сбойная задача крутится бесконечно"
                        + " При смене ticketFailLimit_Is_Five проверь это утверждение.");
        assertEquals(
                5,
                BoardTuning.ticketFailLimit(),
                "Предел провалов задания читается из живой таблицы board: число настраивается датапаком"
                        + " Иначе тест ticketFailLimit_Is_Five перестанет ловить эту поломку. При смене ticketFailLimit_Is_Five проверь это утверждение.");
    }

    @Test
    void caseFailLimit_Is_Three() {
        assertEquals(
                3,
                BoardTuning.CASE_FAIL_LIMIT,
                "Три провала подряд снимают кейс с духа: предел 3 не даёт бесконечному циклу "
                        + "одного и того же исполнителя"
                        + " Иначе тест caseFailLimit_Is_Three перестанет ловить эту поломку. При смене caseFailLimit_Is_Three проверь это утверждение.");
        assertEquals(
                3,
                BoardTuning.caseFailLimit(),
                "Предел провалов читается из живой таблицы board: число настраивается датапаком"
                        + " Иначе тест caseFailLimit_Is_Three перестанет ловить эту поломку. При смене caseFailLimit_Is_Three проверь это утверждение.");
    }

    @Property
    void anyNumber_Is_PositiveAndFinite(@ForAll("boardNumbers") Number number) {
        double value = number.doubleValue();

        assertTrue(
                value > 0,
                "Число доски должно быть положительным: " + number
                        + " Иначе тест anyNumber_Is_PositiveAndFinite перестанет ловить эту поломку. При смене anyNumber_Is_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                Double.isFinite(value),
                "Число доски должно быть конечным: " + number
                        + " Иначе тест anyNumber_Is_PositiveAndFinite перестанет ловить эту поломку. При смене anyNumber_Is_PositiveAndFinite проверь это утверждение.");
    }

    @Provide
    Arbitrary<Number> boardNumbers() {
        return Arbitraries.of(
                BoardTuning.BOARD_INTERVAL_TICKS,
                (long) BoardTuning.MAX_ACTIVE_PER_SPIRIT,
                (long) BoardTuning.MAX_PENDING,
                (long) BoardTuning.CRATER_TERRITORY_RADIUS,
                (long) BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS,
                (long) BoardTuning.MAX_PRIORITY,
                (long) BoardTuning.STOCK_READ_BUDGET,
                (long) BoardTuning.STOCK_BLOCK_BUDGET,
                BoardTuning.FAIL_BACKOFF_TICKS,
                BoardTuning.CASE_FAIL_PAUSE_TICKS,
                BoardTuning.CASE_FAIL_MULTIPLIER,
                BoardTuning.CASE_FAIL_PAUSE_CAP_TICKS,
                (long) BoardTuning.CASE_FAIL_LIMIT,
                (long) BoardTuning.TICKET_FAIL_LIMIT,
                (long) BoardTuning.FENCE_WATCH_RADIUS);
    }
}
