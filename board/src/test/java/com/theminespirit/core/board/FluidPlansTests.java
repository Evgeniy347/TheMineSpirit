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
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.PourBucketParams;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.FillBucketParams;
import com.theminespirit.core.script.api.FluidKind;
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
                "Вода идёт по цепочке: взять ведро, дойти до источника, набрать, донести, сдать"
                        + " Иначе тест fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket перестанет ловить эту поломку. При смене fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket проверь это утверждение.");
        assertEquals(
                "bucket",
                calls.get(0).params().get("item"),
                "Сначала берут пустое ведро"
                        + " Иначе тест fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket перестанет ловить эту поломку. При смене fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket проверь это утверждение.");
        assertEquals(
                "water",
                calls.get(2).params().get("fluid"),
                "Набирают именно воду"
                        + " Иначе тест fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket перестанет ловить эту поломку. При смене fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket проверь это утверждение.");
        assertEquals(
                "water_bucket",
                calls.get(4).params().get("item"),
                "В склад сдают полное ведро воды"
                        + " Иначе тест fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket перестанет ловить эту поломку. При смене fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket проверь это утверждение.");
        assertEquals(
                SOURCE,
                point(calls.get(1)),
                "Дух идёт к найденному источнику"
                        + " Иначе тест fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket перестанет ловить эту поломку. При смене fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket проверь это утверждение.");
        assertEquals(
                STORE,
                point(calls.get(3)),
                "Дух несёт ведро в точку склада"
                        + " Иначе тест fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket перестанет ловить эту поломку. При смене fetch_Plan_AcquiresBucketFillsAtSourceAndDepositsFullBucket проверь это утверждение.");
    }

    @Test
    void waterCrops_Plan_PoursWaterIntoPlannedCell() {
        BlockCoord cell = new BlockCoord(21, 64, 21);
        TaskNode plan = FluidPlans.waterCrops(FluidKind.WATER, SOURCE, cell);
        List<ScriptCall> calls = calls(plan);

        assertEquals(
                "pour_bucket",
                calls.get(calls.size() - 1).script(),
                "Полив заканчивается выливанием ведра"
                        + " Иначе тест waterCrops_Plan_PoursWaterIntoPlannedCell перестанет ловить эту поломку. При смене waterCrops_Plan_PoursWaterIntoPlannedCell проверь это утверждение.");
        ScriptCall pour = calls.get(calls.size() - 1);
        assertEquals(
                cell.x(),
                pour.params().get("x"),
                "Вода льётся в клетку плана фермы"
                        + " Иначе тест waterCrops_Plan_PoursWaterIntoPlannedCell перестанет ловить эту поломку. При смене waterCrops_Plan_PoursWaterIntoPlannedCell проверь это утверждение.");
        assertEquals(
                cell.z(),
                pour.params().get("z"),
                "Вода льётся в клетку плана фермы"
                        + " Иначе тест waterCrops_Plan_PoursWaterIntoPlannedCell перестанет ловить эту поломку. При смене waterCrops_Plan_PoursWaterIntoPlannedCell проверь это утверждение.");
    }

    @Test
    void waterCrops_StandBesideSource_GoesToStandAndPoursIntoCell() {
        BlockCoord stand = new BlockCoord(9, 62, 10);
        BlockCoord cell = new BlockCoord(21, 64, 21);
        List<ScriptCall> calls = calls(FluidPlans.waterCrops(FluidKind.WATER, SOURCE, stand, cell));

        assertEquals(
                stand,
                point(calls.get(1)),
                "Дух идёт на стоянку у воды, а не в клетку источника"
                        + " Иначе тест waterCrops_StandBesideSource_GoesToStandAndPoursIntoCell перестанет ловить эту поломку. При смене waterCrops_StandBesideSource_GoesToStandAndPoursIntoCell проверь это утверждение.");
        assertEquals(
                cell.x(),
                calls.get(calls.size() - 1).params().get("x"),
                "Льёт в клетку плана"
                        + " Иначе тест waterCrops_StandBesideSource_GoesToStandAndPoursIntoCell перестанет ловить эту поломку. При смене waterCrops_StandBesideSource_GoesToStandAndPoursIntoCell проверь это утверждение.");
    }

    @Test
    void farmWaterCell_PlanWithTwoCells_ReturnsBothForRefill() {
        Schematic scheme = new Schematic(
                7,
                2,
                12,
                List.of(
                        new Schematic.Entry(3, 0, 3, "minecraft:water"),
                        new Schematic.Entry(3, 0, 8, "minecraft:water")),
                List.of());

        assertEquals(
                List.of(new BlockCoord(3, -1, 3), new BlockCoord(3, -1, 8)),
                FarmWaterCell.plannedAll(scheme, new BlockCoord(0, 0, 0)),
                "Полив видит обе клетки воды в поверхности поля: иначе вторая ямка останется сухой"
                        + " При смене farmWaterCell_PlanWithTwoCells_ReturnsBothForRefill проверь это утверждение.");
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
                    "План с ведром обязан проходить каталог скриптов без подмены шагов"
                            + " Иначе тест plans_CatalogValidated_KeepSameScripts перестанет ловить эту поломку. При смене plans_CatalogValidated_KeepSameScripts проверь это утверждение.");
        }
    }

    @Test
    void fillParams_DefaultRadius_AndUnknownFluidRejected() {
        assertEquals(
                FillBucketParams.RADIUS_DEFAULT,
                FillBucketParams.from(Map.of("fluid", "lava")).radius(),
                "Без радиуса берут значение по умолчанию"
                        + " Иначе тест fillParams_DefaultRadius_AndUnknownFluidRejected перестанет ловить эту поломку. При смене fillParams_DefaultRadius_AndUnknownFluidRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> FillBucketParams.from(Map.of("fluid", "milk")),
                "Неизвестная жидкость не должна доходить до скрипта"
                        + " Иначе тест fillParams_DefaultRadius_AndUnknownFluidRejected перестанет ловить эту поломку. При смене fillParams_DefaultRadius_AndUnknownFluidRejected проверь это утверждение.");
    }

    @Test
    void pourParams_OutOfWorldY_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PourBucketParams.from(Map.of("fluid", "water", "x", 0, "y", 1000, "z", 0)),
                "Ведро не льют за пределы мира"
                        + " Иначе тест pourParams_OutOfWorldY_Rejected перестанет ловить эту поломку. При смене pourParams_OutOfWorldY_Rejected проверь это утверждение.");
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
                new BlockCoord(104, 63, -46),
                FarmWaterCell.planned(scheme, origin).orElseThrow(),
                "Вода ставится в поверхность поля, в клетку схемы по x/z, а не в любую лужу рядом"
                        + " Иначе тест farmWaterCell_ReadsWaterFromPlan_NotFromGuess перестанет ловить эту поломку. При смене farmWaterCell_ReadsWaterFromPlan_NotFromGuess проверь это утверждение.");
        assertTrue(
                FarmWaterCell.field("wheat_field"),
                "Поле узнаётся по id"
                        + " Иначе тест farmWaterCell_ReadsWaterFromPlan_NotFromGuess перестанет ловить эту поломку. При смене farmWaterCell_ReadsWaterFromPlan_NotFromGuess проверь это утверждение.");
        assertTrue(
                FarmWaterCell.field("field_a"),
                "Поле этапа узнаётся по id"
                        + " Иначе тест farmWaterCell_ReadsWaterFromPlan_NotFromGuess перестанет ловить эту поломку. При смене farmWaterCell_ReadsWaterFromPlan_NotFromGuess проверь это утверждение.");
        assertFalse(
                FarmWaterCell.field("barn"),
                "Сарай не считается полем"
                        + " Иначе тест farmWaterCell_ReadsWaterFromPlan_NotFromGuess перестанет ловить эту поломку. При смене farmWaterCell_ReadsWaterFromPlan_NotFromGuess проверь это утверждение.");
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
