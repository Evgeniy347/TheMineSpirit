package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class StreetWorkTests {
    private static final PresetLibrary PRESETS = DataTables.INSTANCE.presets().current();
    private static final StageRules STAGES = DataTables.INSTANCE.stages().current();
    private static final List<String> TILES = List.of(
            "street5_straight",
            "street5_turn",
            "street5_t",
            "street5_cross",
            "street7_straight",
            "street7_turn",
            "street7_t",
            "street7_cross",
            StreetLayout.PATCH);

    private static Set<String> materials(int stage) {
        int level = StreetWork.level(STAGES, stage);
        Set<String> blocks = new TreeSet<>();
        for (String tile : TILES) {
            String scheme = StreetWork.scheme(tile, level);
            blocks.addAll(PresetLibrary.countMaterials(
                            PRESETS.byId(scheme).orElseThrow().schematic())
                    .keySet());
        }
        return blocks;
    }

    @Test
    void scheme_StageOne_TilesOnlyDirt() {
        assertEquals(
                Set.of("minecraft:dirt"),
                materials(1),
                "плитки улиц этапа 1 — только земля: тропу и гравий духам на первом этапе не добыть");
    }

    @Test
    void scheme_StagesTwoThree_PathAndGravel() {
        for (int stage : new int[] {2, 3}) {
            Set<String> blocks = materials(stage);
            assertTrue(
                    blocks.contains("minecraft:dirt_path") && blocks.contains("minecraft:gravel"),
                    "этап " + stage + ": плитки улиц — тропа с гравийными краями, а не " + blocks);
            assertFalse(blocks.contains("minecraft:dirt"), "этап " + stage + ": земляных плиток нет: " + blocks);
        }
    }

    @Test
    void of_FlatSite_TilesOnLevelledGroundOutsidePostsAndPlaces() {
        StageSite site = TownFixtures.flatSite();
        List<SchemeBuild> builds = StreetWork.of(
                site,
                TownFixtures.flatPlan(),
                DataTables.INSTANCE.town().current().streets(),
                PRESETS,
                StreetWork.level(STAGES, 1));
        assertFalse(builds.isEmpty(), "на выровненных площадках этапа 1 есть улицы: плиткам есть где лечь");
        ColumnMask within = StreetWork.within(site);
        for (SchemeBuild build : builds) {
            assertEquals(site.anchor().y() - 1, build.origin().y(), "плитка ложится в слой земли под ногами");
            for (Schematic.PlacedEntry entry :
                    PRESETS.byId(build.scheme()).orElseThrow().schematic().entries(build.origin(), build.rotation())) {
                assertTrue(
                        within.contains(entry.pos().x(), entry.pos().z()),
                        "плитка " + build + " вышла за выровненную улицу — на столб ограды или постройку");
            }
        }
    }
}
