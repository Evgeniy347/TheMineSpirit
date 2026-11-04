package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.FillBucketParams;
import com.theminespirit.core.script.FluidKind;
import com.theminespirit.core.script.PourBucketParams;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FluidPlansTests {
    private static final BlockCoord SOURCE = new BlockCoord(10, 62, 10);
    private static final BlockCoord STORE = new BlockCoord(20, 64, 20);

    @Test
    void fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket() {
        TaskNode plan = FluidPlans.fetch(FluidKind.WATER, SOURCE, STORE);
        List<ScriptCall> calls = calls(plan);

        assertEquals(
                List.of("acquire", "goto", "fill_bucket", "goto", "deposit"),
                calls.stream().map(ScriptCall::script).toList(),
                "Вода идёт по цепочке: взять ведро, дойти до источника, набрать, донести, сдать");
        assertEquals("bucket", calls.get(0).params().get("item"), "Сначала берут пустое ведро");
        assertEquals("water", calls.get(2).params().get("fluid"), "Набирают именно воду");
        assertEquals("water_bucket", calls.get(4).params().get("item"), "В склад сдают полное ведро воды");
        assertEquals(SOURCE, point(calls.get(1)), "Дух идёт к найденному источнику");
        assertEquals(STORE, point(calls.get(3)), "Дух несёт ведро в точку склада");
    }

    @Test
    void waterCrops_Plan_PoursWaterIntoPlannedCell() {
        BlockCoord cell = new BlockCoord(21, 64, 21);
        TaskNode plan = FluidPlans.waterCrops(FluidKind.WATER, SOURCE, cell);
        List<ScriptCall> calls = calls(plan);

        assertEquals("pour_bucket", calls.get(calls.size() - 1).script(), "Полив заканчивается выливанием ведра");
        ScriptCall pour = calls.get(calls.size() - 1);
        assertEquals(cell.x(), pour.params().get("x"), "Вода льётся в клетку плана фермы");
        assertEquals(cell.z(), pour.params().get("z"), "Вода льётся в клетку плана фермы");
    }

    @Test
    void plans_CatalogValidated_KeepSameScripts() {
        for (TaskNode plan : List.of(
                FluidPlans.fetch(FluidKind.WATER, SOURCE, STORE),
                FluidPlans.fetch(FluidKind.LAVA, SOURCE, STORE),
                FluidPlans.waterCrops(FluidKind.WATER, SOURCE, STORE))) {
            assertEquals(
                    calls(plan).stream().map(ScriptCall::script).toList(),
                    calls(PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty())).stream()
                            .map(ScriptCall::script)
                            .toList(),
                    "План с ведром обязан проходить каталог скриптов без подмены шагов");
        }
    }

    @Test
    void fillParams_DefaultRadius_AndUnknownFluidRejected() {
        assertEquals(
                FillBucketParams.RADIUS_DEFAULT,
                FillBucketParams.from(Map.of("fluid", "lava")).radius(),
                "Без радиуса берут значение по умолчанию");
        assertThrows(
                IllegalArgumentException.class,
                () -> FillBucketParams.from(Map.of("fluid", "milk")),
                "Неизвестная жидкость не должна доходить до скрипта");
    }

    @Test
    void pourParams_OutOfWorldY_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PourBucketParams.from(Map.of("fluid", "water", "x", 0, "y", 1000, "z", 0)),
                "Ведро не льют за пределы мира");
    }

    @Test
    void farmWaterCell_ReadsWaterFromPlan_NotFromGuess() {
        Schematic scheme = new Schematic(
                9,
                2,
                9,
                List.of(
                        new Schematic.Entry(4, 0, 4, "minecraft:water"),
                        new Schematic.Entry(0, 0, 0, "minecraft:farmland")),
                List.of());
        BlockCoord origin = new BlockCoord(100, 64, -50);

        assertEquals(
                new BlockCoord(104, 64, -46),
                FarmWaterCell.planned(scheme, origin).orElseThrow(),
                "Вода ставится в клетку схемы поля, а не в любую лужу рядом");
        assertTrue(FarmWaterCell.field("wheat_field"), "Поле узнаётся по id");
        assertTrue(FarmWaterCell.field("field_a"), "Поле этапа узнаётся по id");
        assertFalse(FarmWaterCell.field("barn"), "Сарай не считается полем");
    }

    private static List<ScriptCall> calls(TaskNode plan) {
        return ((SequenceNode) plan)
                .steps().stream()
                        .filter(ScriptCall.class::isInstance)
                        .map(ScriptCall.class::cast)
                        .toList();
    }

    private static BlockCoord point(ScriptCall call) {
        return new BlockCoord(
                (Integer) call.params().get("x"), (Integer) call.params().get("y"), (Integer)
                        call.params().get("z"));
    }
}
