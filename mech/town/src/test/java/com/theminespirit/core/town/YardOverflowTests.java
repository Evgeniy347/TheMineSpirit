package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class YardOverflowTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void yardStock_SlotsExhausted_AddsOverflowPair() {
        int next = YardScheme.slots(SITE).size() + 1;

        List<SchemeBuild> builds = YardScheme.stock(SITE, next);

        assertFalse(
                builds.isEmpty(),
                "ряд сундуков двора занят, но при заполнении <20% нужна новая пара: схема yard_chest "
                        + "ставит её в свободное место двора, а не бросает задание"
                        + " Иначе тест yardStock_SlotsExhausted_AddsOverflowPair перестанет ловить эту поломку. При смене yardStock_SlotsExhausted_AddsOverflowPair проверь это утверждение.");
        SchemeBuild build = builds.get(0);
        assertEquals(
                YardScheme.CHESTS,
                build.scheme(),
                "новая пара строится схемой yard_chest"
                        + " Иначе тест yardStock_SlotsExhausted_AddsOverflowPair перестанет ловить эту поломку. При смене yardStock_SlotsExhausted_AddsOverflowPair проверь это утверждение.");
        assertEquals(
                SITE.anchor().y(),
                build.origin().y(),
                "пара стоит на уровне двора, не выше и не ниже"
                        + " Иначе тест yardStock_SlotsExhausted_AddsOverflowPair перестанет ловить эту поломку. При смене yardStock_SlotsExhausted_AddsOverflowPair проверь это утверждение.");
        assertTrue(
                SITE.yardColumn(build.origin().x(), build.origin().z())
                        && SITE.yardColumn(
                                build.origin().x() + 1, build.origin().z()),
                "обе половины новой пары внутри двора: иначе OldChests сочтёт её старым сундуком и унесёт: "
                        + build.origin()
                        + " При смене yardStock_SlotsExhausted_AddsOverflowPair проверь это утверждение.");
    }

    @Test
    void yardStock_OverflowExhausted_ThrowsWithLimit() {
        int beyond =
                YardScheme.slots(SITE).size() + YardScheme.overflowSlots(SITE).size() + 1;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> YardScheme.stock(SITE, beyond),
                "место сверх дворового резерва не должно ставиться молча"
                        + " Иначе тест yardStock_OverflowExhausted_ThrowsWithLimit перестанет ловить эту поломку. При смене yardStock_OverflowExhausted_ThrowsWithLimit проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("мест"),
                "отказ называет предел мест, а не падает молча: " + error.getMessage()
                        + " Иначе тест yardStock_OverflowExhausted_ThrowsWithLimit перестанет ловить эту поломку. При смене yardStock_OverflowExhausted_ThrowsWithLimit проверь это утверждение.");
    }

    @Test
    void yardOverflow_PairsInsideYard_AreDistinct() {
        List<List<BlockCoord>> overflow = YardScheme.overflowSlots(SITE);

        assertFalse(
                overflow.isEmpty(),
                "во дворе есть свободные клетки под пару: иначе переполнение некуда ставить"
                        + " При смене yardOverflow_PairsInsideYard_AreDistinct проверь это утверждение.");
        for (List<BlockCoord> pair : overflow) {
            assertEquals(
                    2,
                    pair.size(),
                    "место переполнения — ровно пара сундуков: " + pair
                            + " Иначе тест yardOverflow_PairsInsideYard_AreDistinct перестанет ловить эту поломку. При смене yardOverflow_PairsInsideYard_AreDistinct проверь это утверждение.");
            assertTrue(
                    SITE.yardColumn(pair.get(0).x(), pair.get(0).z())
                            && SITE.yardColumn(pair.get(1).x(), pair.get(1).z()),
                    "обе половины пары внутри двора: " + pair
                            + " Иначе тест yardOverflow_PairsInsideYard_AreDistinct перестанет ловить эту поломку. При смене yardOverflow_PairsInsideYard_AreDistinct проверь это утверждение.");
        }
        assertEquals(
                overflow.size(),
                overflow.stream().map(pair -> pair.get(0)).distinct().count(),
                "места переполнения не повторяются: иначе вторая пара встанет поверх первой"
                        + " При смене yardOverflow_PairsInsideYard_AreDistinct проверь это утверждение.");
    }

    @Test
    void yardStock_OverflowSlotAlreadyUsed_NextFreeSlot() {
        int first = YardScheme.slots(SITE).size() + 1;

        SchemeBuild one = YardScheme.stock(SITE, first).get(0);
        SchemeBuild two = YardScheme.stock(SITE, first + 1).get(0);

        assertTrue(
                !one.origin().equals(two.origin()),
                "каждая следующая переполненная пара занимает своё место, а не то же самое: " + one.origin() + " и "
                        + two.origin()
                        + " Иначе тест yardStock_OverflowSlotAlreadyUsed_NextFreeSlot перестанет ловить эту поломку. При смене yardStock_OverflowSlotAlreadyUsed_NextFreeSlot проверь это утверждение.");
    }

    @Test
    void yardOverflow_YardArea_GivesMoreThan16Pairs() {
        List<List<BlockCoord>> overflow = YardScheme.overflowSlots(SITE);

        assertEquals(
                30,
                overflow.size(),
                "двор 7×5 даёт 6 пар в ряд на 5 рядов: прежний предел 16 отрезал 14 мест, и при полном ряде "
                        + "склад не расширялся, а свободные слоты падали ниже 20%; иначе рост упирается в "
                        + "пустые клетки двора. При смене схемы yard_1 или MAX_OVERFLOW_PAIRS проверь "
                        + "YardOverflowTests и живую постановку пар после публикации");
    }
}
