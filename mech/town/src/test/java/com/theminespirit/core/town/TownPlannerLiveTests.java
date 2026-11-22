package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.observation.BlockCoord;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownPlannerLiveTests {
    private static final TownRules RULES = TownRules.load();
    private static final List<List<String>> STAGES = StageRules.load().plans();
    private static final BlockCoord ANCHOR = new BlockCoord(200, 70, -300);

    @Test
    void terrain_UnloadedColumn_IsNotReadyForPlan() {
        int radius = 1;
        int[] heights = {70, 70, 70, 70, 70, 70, 70, 70, TerrainGrid.NO_GROUND};
        TerrainGrid partial = TerrainGrid.around(ANCHOR, radius, heights, new BitSet());

        assertFalse(
                partial.loaded(),
                "Колонка без загруженного чанка делает рельеф неготовым: план по недогруженному рельефу "
                        + "разметил бы один столб"
                        + " Иначе тест terrain_UnloadedColumn_IsNotReadyForPlan перестанет ловить эту поломку. При смене terrain_UnloadedColumn_IsNotReadyForPlan проверь это утверждение.");
        assertTrue(
                grid(ANCHOR, RULES.ground().probeRadius(), 70).loaded(),
                "Рельеф без пустых колонок готов: ожидание загрузки не задерживает первый план"
                        + " Иначе тест terrain_UnloadedColumn_IsNotReadyForPlan перестанет ловить эту поломку. При смене terrain_UnloadedColumn_IsNotReadyForPlan проверь это утверждение.");
    }

    @Test
    void laidWith_OldProbeRadius_RejectsSavedPlan() {
        TownPlan old = TownPlanner.plan(grid(ANCHOR, 64, 70), ANCHOR, RULES, STAGES);

        assertEquals(
                129,
                old.region().width(),
                "Снимок плана со старым обмером 64 — 129 колонок по стороне"
                        + " Иначе тест laidWith_OldProbeRadius_RejectsSavedPlan перестанет ловить эту поломку. При смене laidWith_OldProbeRadius_RejectsSavedPlan проверь это утверждение.");
        assertFalse(
                TownPlanner.laidWith(old, RULES),
                "План, размеченный обмером 64, не принимается при обмере "
                        + RULES.ground().probeRadius() + ": иначе склад и вся этапная стройка остались бы без места"
                        + " При смене laidWith_OldProbeRadius_RejectsSavedPlan проверь это утверждение.");
        TownPlan fresh = TownPlanner.plan(grid(ANCHOR, RULES.ground().probeRadius(), 70), ANCHOR, RULES, STAGES);
        assertTrue(
                TownPlanner.laidWith(fresh, RULES),
                "План по текущим правилам обмера считается своим: пересборка на каждом тике не нужна"
                        + " Иначе тест laidWith_OldProbeRadius_RejectsSavedPlan перестанет ловить эту поломку. При смене laidWith_OldProbeRadius_RejectsSavedPlan проверь это утверждение.");
    }

    @Test
    void savedLivePlan_StorageUnplaced_IsStaleForCurrentRules() throws Exception {
        TownPlan live = TownPlanCodec.decode(fixture("/live/warehouselot/town-plan.txt"));

        assertTrue(
                live.placement("storage").isEmpty(),
                "Живой план observe18 без лота storage: именно поэтому этап 2 просил склад у стратега"
                        + " Иначе тест savedLivePlan_StorageUnplaced_IsStaleForCurrentRules перестанет ловить эту поломку. При смене savedLivePlan_StorageUnplaced_IsStaleForCurrentRules проверь это утверждение.");
        assertTrue(
                live.unplaced().contains("storage"),
                "Живой план держит storage в непоместившихся: " + live.unplaced()
                        + " Иначе тест savedLivePlan_StorageUnplaced_IsStaleForCurrentRules перестанет ловить эту поломку. При смене savedLivePlan_StorageUnplaced_IsStaleForCurrentRules проверь это утверждение.");
        assertFalse(
                TownPlanner.laidWith(live, RULES),
                "Живой план размечен обмером 64 (129 колонок), а правила требуют "
                        + (2 * RULES.ground().probeRadius() + 1) + ": план обязан пересобраться"
                        + " Иначе тест savedLivePlan_StorageUnplaced_IsStaleForCurrentRules перестанет ловить эту поломку. При смене savedLivePlan_StorageUnplaced_IsStaleForCurrentRules проверь это утверждение.");
    }

    @Test
    void plan_FlatLand_StorageLotPlacedInsidePlot() {
        TownPlan plan = TownPlanner.plan(grid(ANCHOR, RULES.ground().probeRadius(), 70), ANCHOR, RULES, STAGES);

        Placement storage = plan.placement("storage")
                .orElseThrow(
                        () -> new AssertionError("Склад этапа 2 без лота в плане: не поместилось " + plan.unplaced()));
        assertEquals(
                1,
                storage.stage(),
                "Склад — постройка этапа 1: место берётся из набора этапа"
                        + " Иначе тест plan_FlatLand_StorageLotPlacedInsidePlot перестанет ловить эту поломку. При смене plan_FlatLand_StorageLotPlacedInsidePlot проверь это утверждение.");
        for (ColumnMask.Cell cell : storage.mask().cells()) {
            assertTrue(
                    plan.onPlot(cell.x(), cell.z()),
                    "Клетка " + cell + " склада вне контура поселения: склад стоял бы за оградой"
                            + " Иначе тест plan_FlatLand_StorageLotPlacedInsidePlot перестанет ловить эту поломку. При смене plan_FlatLand_StorageLotPlacedInsidePlot проверь это утверждение.");
            assertFalse(
                    plan.streets().contains(cell.x(), cell.z()),
                    "Клетка " + cell + " склада лежит на улице: улица — общий проход"
                            + " Иначе тест plan_FlatLand_StorageLotPlacedInsidePlot перестанет ловить эту поломку. При смене plan_FlatLand_StorageLotPlacedInsidePlot проверь это утверждение.");
        }
    }

    private static String fixture(String resource) throws Exception {
        try (InputStream in = TownPlannerLiveTests.class.getResourceAsStream(resource)) {
            assertTrue(
                    in != null,
                    "Нет фикстуры " + resource
                            + " Иначе тест plan_FlatLand_StorageLotPlacedInsidePlot перестанет ловить эту поломку. При смене plan_FlatLand_StorageLotPlacedInsidePlot проверь это утверждение.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static TerrainGrid grid(BlockCoord anchor, int radius, int feet) {
        int side = 2 * radius + 1;
        int[] heights = new int[side * side];
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                heights[TerrainGrid.index(radius, dx, dz)] = feet;
            }
        }
        return TerrainGrid.around(anchor, radius, heights, new BitSet());
    }
}
