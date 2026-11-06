package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class PillarPlanProperties {
    @Property
    void plan_AnyHeight_CountEqualsHeightAndEveryStepOneAbove(@ForAll int rawHeight) {
        int height = Math.floorMod(rawHeight, 32) + 1;
        List<BlockCoord> plan = PillarPlan.plan(height);

        assertEquals(height, plan.size(), "блоков в плане = высота " + height);
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(i, plan.get(i).y(), "ступень " + i + " обязана быть на " + i + " блоков выше основания");
        }
    }

    @Property
    void dismantle_AnyHeight_RemovesEveryPlannedCell(@ForAll int rawHeight) {
        int height = Math.floorMod(rawHeight, 32) + 1;
        Set<BlockCoord> own = new HashSet<>(PillarPlan.plan(height));

        own.removeAll(PillarPlan.dismantle(height));

        assertTrue(own.isEmpty(), "после разбора высоты " + height + " своих блоков не осталось: " + own);
    }
}
