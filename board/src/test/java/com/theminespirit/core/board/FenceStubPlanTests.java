package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FenceStubPlanTests {
    private static final BlockCoord ORIGIN = new BlockCoord(-170, 70, -489);

    @Test
    void of_FirstStage_UsesSingleFencePost() {
        TaskNode node = FenceStubPlan.of(ORIGIN, 1, "oak");
        ScriptCall call = (ScriptCall) node;

        assertEquals("build_schematic", call.script(), "Заглушка ставится схемой, как и весь забор этапа");
        assertEquals(
                "fence_post",
                call.params().get("file"),
                "Событие ставит один столб в опасной колонке, а не кольцо 23×23: кольцо оставлено REST/демо");
        assertEquals(
                Map.of("x", -170, "y", 70, "z", -489),
                Map.of(
                        "x", call.params().get("x"),
                        "y", call.params().get("y"),
                        "z", call.params().get("z")),
                "Заглушка стоит в основании колонки опасности: координаты берутся из SiteLevels.fenceOrigin");
    }

    @Test
    void of_SecondStage_UsesSingleWallPost() {
        ScriptCall call = (ScriptCall) FenceStubPlan.of(ORIGIN, 2, "spruce");

        assertEquals(
                "wall_3_post",
                call.params().get("file"),
                "Со второго этапа ограда — стена: заглушка обязана быть стенным столбом, а не забором прошлого этапа");
    }

    @Test
    void of_WoodAndNoWood_BothValidPlans() {
        ScriptCall wood = (ScriptCall) FenceStubPlan.of(ORIGIN, 1, "birch");
        ScriptCall any = (ScriptCall) FenceStubPlan.of(ORIGIN, 1, null);

        assertEquals("birch", wood.params().get("wood"), "Порода поселения передаётся схеме заглушки");
        assertFalse(
                any.params().containsKey("wood"),
                "Без породы схема берёт свою: пустое имя породы сломало бы build_schematic");
    }
}
