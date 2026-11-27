package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchematicFieldRepairTests {
    @Test
    void supply_MissingWater_RequiresFilledBucket() throws IOException {
        String need = compact("BuildSupplyNeed.java");
        String trip = compact("BuildSupplyTrip.java");

        assertTrue(
                need.contains("SchematicActions.isFluidPlaced(entry.block())")
                        && need.contains("SchematicBlocks.itemOf(entry.block())")
                        && trip.contains("SchematicActions.isFluidPlaced(entry.block())"),
                "Потерянная вода входит в подвоз заполненным ведром и не выбрасывается как лишний предмет: "
                        + "иначе ремонт поля не получает материал и снова оставляет сухую клетку"
                        + " При смене supply_MissingWater_RequiresFilledBucket проверь BuildSupplyNeed и BuildSupplyTrip.");
    }

    @Test
    void water_NoSource_FetchesFilledBucketBeforeRefusal() throws IOException {
        String source = compact("BuildWater.java");

        assertTrue(
                source.contains("BuildSupplyTrip.exact(") && source.contains("FluidPlans.pourFromBucket("),
                "Без природного источника стройка сначала берёт заполненное ведро из сундука: иначе "
                        + "восстановление воды невозможно даже при готовом запасе"
                        + " При смене water_NoSource_FetchesFilledBucketBeforeRefusal проверь BuildWater.plan.");
    }

    @Test
    void plan_TilledScheme_SkipsPlannerSoWaterPrecedesCrops() throws IOException {
        String source = compact("SchematicPlanPhase.java");
        int guard = source.indexOf("SchematicGround.tilled(host.site.schematic())");
        int planner = source.indexOf("CompletableFuture.supplyAsync(");

        assertTrue(
                guard >= 0 && planner > guard,
                "Пашня не отдаёт свет и семена общему планировщику раньше воды: иначе finishOrder "
                        + "обходится и поле снова строится в неверном порядке"
                        + " При смене plan_TilledScheme_SkipsPlannerSoWaterPrecedesCrops проверь SchematicPlanPhase.startPlan.");
    }

    @Test
    void base_GroundedScheme_UsesShiftedGroundY() throws IOException {
        String source = compact("SchematicFieldBase.java");

        assertTrue(
                source.contains("intgroundY=groundY(") && source.contains("entry.pos().y()==groundY"),
                "Основание ищется на фактическом y0 grounded-схемы: иначе сдвиг на origin.y()-1 даёт "
                        + "пустой список и земля под пашней не готовится"
                        + " При смене base_GroundedScheme_UsesShiftedGroundY проверь SchematicFieldBase.");
    }

    @Test
    void state_FarmlandMoisture_IsDynamic() throws IOException {
        String source = compact("SchematicSite.java");

        assertTrue(
                source.contains("DERIVED=Set.of(\"shape\",\"moisture\")"),
                "Влажность пашни не сверяется как постоянное состояние схемы: иначе moisture=0 запускает "
                        + "бесконечный repairState вместо восстановления источника воды"
                        + " При смене state_FarmlandMoisture_IsDynamic проверь SchematicSite.DERIVED.");
    }

    private static String compact(String file) throws IOException {
        return Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge", file))
                .replaceAll("\\s+", "");
    }
}
