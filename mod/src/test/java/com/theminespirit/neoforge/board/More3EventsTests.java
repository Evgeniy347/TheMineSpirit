package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import org.junit.jupiter.api.Test;

class More3EventsTests {
    @Test
    void dangerBase_DangerAboveSettlement_UsesFenceLevel() {
        StageSite site = TownFixtures.flatSite();
        BlockCoord roof = new BlockCoord(-170, TownFixtures.LEVEL + 10, -489);

        assertEquals(
                new BlockCoord(roof.x(), TownFixtures.LEVEL, roof.z()),
                More3Events.dangerBase(site, roof),
                "Событие пишет основание колонки (уровень ограды), а не ноги духа на крыше склада: иначе "
                        + "avoidDanger и забор получают произвольную y=80");
    }

    @Test
    void dangerBase_NoSettlement_KeepsFeet() {
        BlockCoord feet = new BlockCoord(5, 64, 6);

        assertEquals(
                feet,
                More3Events.dangerBase((StageSite) null, feet),
                "До появления площадки поселения основания колонки нет: запись остаётся на ногах и мигрирует, "
                        + "когда уровень ограды станет известен");
    }
}
