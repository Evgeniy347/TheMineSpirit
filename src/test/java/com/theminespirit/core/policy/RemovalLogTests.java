package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class RemovalLogTests {
    @Test
    void summary_TwoLogsAndDirt_NamesWhatHowManyWhere() {
        RemovalLog log = new RemovalLog();
        log.removed("minecraft:oak_log", new BlockCoord(-169, 71, -501));
        log.removed("minecraft:oak_log", new BlockCoord(-169, 72, -501));
        log.removed("minecraft:dirt", new BlockCoord(-170, 69, -500));

        assertEquals(
                "minecraft:oak_log×2, minecraft:dirt×1 (всего 3, первые [BlockCoord[x=-169, y=71, z=-501], "
                        + "BlockCoord[x=-169, y=72, z=-501], BlockCoord[x=-170, y=69, z=-500]])",
                log.summary(),
                "Каждое снятие духом вне ожидаемого видно в логе одной строкой на стройку: что, сколько и где — "
                        + "иначе пропажу ствола не найти (INC-89)");
    }
}
