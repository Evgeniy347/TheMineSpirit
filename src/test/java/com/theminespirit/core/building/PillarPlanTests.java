package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class PillarPlanTests {
    @Test
    void of_ThreeBlocks_StepsOneAboveAnother() {
        List<BlockCoord> plan = PillarPlan.of(3);
        assertEquals(3, plan.size(), "блоков в плане = высота");
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(i, plan.get(i).y(), "каждая ступень на блок выше");
        }
    }
}

class PillarPlanProperties {
    @Property
    void plan_AnyHeight_EveryStepOneAboveAndCountEqualsHeight(@ForAll int height) {
        int h = Math.floorMod(height, 20) + 1;
        List<BlockCoord> plan = PillarPlan.of(h);
        assertEquals(h, plan.size(), "блоков в плане = высота");
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(i, plan.get(i).y(), "каждая ступень на блок выше");
        }
    }
}
