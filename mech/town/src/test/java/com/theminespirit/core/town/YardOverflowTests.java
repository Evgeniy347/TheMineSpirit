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
                        + "ставит её в свободное место двора, а не бросает задание");
        SchemeBuild build = builds.get(0);
        assertEquals(YardScheme.CHESTS, build.scheme(), "новая пара строится схемой yard_chest");
        assertEquals(SITE.anchor().y(), build.origin().y(), "пара стоит на уровне двора, не выше и не ниже");
        assertTrue(
                SITE.yardColumn(build.origin().x(), build.origin().z())
                        && SITE.yardColumn(
                                build.origin().x() + 1, build.origin().z()),
                "обе половины новой пары внутри двора: иначе OldChests сочтёт её старым сундуком и унесёт: "
                        + build.origin());
    }

    @Test
    void yardStock_OverflowExhausted_ThrowsWithLimit() {
        int beyond =
                YardScheme.slots(SITE).size() + YardScheme.overflowSlots(SITE).size() + 1;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> YardScheme.stock(SITE, beyond),
                "место сверх дворового резерва не должно ставиться молча");

        assertTrue(
                error.getMessage().contains("мест"),
                "отказ называет предел мест, а не падает молча: " + error.getMessage());
    }

    @Test
    void yardOverflow_PairsInsideYard_AreDistinct() {
        List<List<BlockCoord>> overflow = YardScheme.overflowSlots(SITE);

        assertFalse(overflow.isEmpty(), "во дворе есть свободные клетки под пару: иначе переполнение некуда ставить");
        for (List<BlockCoord> pair : overflow) {
            assertEquals(2, pair.size(), "место переполнения — ровно пара сундуков: " + pair);
            assertTrue(
                    SITE.yardColumn(pair.get(0).x(), pair.get(0).z())
                            && SITE.yardColumn(pair.get(1).x(), pair.get(1).z()),
                    "обе половины пары внутри двора: " + pair);
        }
        assertEquals(
                overflow.size(),
                overflow.stream().map(pair -> pair.get(0)).distinct().count(),
                "места переполнения не повторяются: иначе вторая пара встанет поверх первой");
    }

    @Test
    void yardStock_OverflowSlotAlreadyUsed_NextFreeSlot() {
        int first = YardScheme.slots(SITE).size() + 1;

        SchemeBuild one = YardScheme.stock(SITE, first).get(0);
        SchemeBuild two = YardScheme.stock(SITE, first + 1).get(0);

        assertTrue(
                !one.origin().equals(two.origin()),
                "каждая следующая переполненная пара занимает своё место, а не то же самое: " + one.origin() + " и "
                        + two.origin());
    }
}
