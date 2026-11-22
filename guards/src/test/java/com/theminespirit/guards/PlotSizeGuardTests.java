package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.TownEntry;
import com.theminespirit.core.town.TownRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlotSizeGuardTests {
    private static final Set<String> SIZES = Set.of("16x16", "32x16", "32x32", "32x64", "64x64");
    private static final PresetLibrary PRESETS = DataTables.INSTANCE.presets().current();

    @Test
    void plots_AllLots_UseDeclaredGridSize() {
        List<String> bad = new ArrayList<>();
        for (TownEntry entry : TownRules.load().buildings()) {
            if ("mine".equals(entry.id())) {
                continue;
            }
            String size = entry.width() + "x" + entry.depth();
            if (!SIZES.contains(size)) {
                bad.add("участок '" + entry.id() + "' " + size + ": допустимы " + SIZES);
            }
            if (entry.width() != entry.depth() && entry.rotate()) {
                bad.add("участок '" + entry.id() + "' " + size + " прямоугольный и не может вращаться: rotate=false");
            }
        }
        assertEquals(
                List.of(),
                bad,
                String.join("\n", bad)
                        + "; иначе участок и схема разойдутся, дом уровня 4 выйдет за участок, и стройка встанет"
                        + " на первом же уровне — проверь размеры и перегенерируй схемы. При смене этого теста"
                        + " проверь это утверждение.");
    }

    @Test
    void schemes_EveryLevel_KeepSamePlotSize() {
        List<String> bad = new ArrayList<>();
        for (TownEntry entry : TownRules.load().buildings()) {
            if ("mine".equals(entry.id())) {
                continue;
            }
            DemoBuildings.Building building = StageCatalog.catalog(entry.id()).orElse(null);
            if (building == null) {
                continue;
            }
            for (DemoBuildings.Level level : building.levels()) {
                Preset preset = PRESETS.byId(level.scheme()).orElse(null);
                if (preset == null) {
                    bad.add("схемы '" + level.scheme() + "' нет в presets.json");
                    continue;
                }
                int sx = preset.schematic().sizeX();
                int sz = preset.schematic().sizeZ();
                if (sx != entry.width() || sz != entry.depth()) {
                    bad.add("у постройки '" + entry.id() + "' уровень " + level.level() + ": участок "
                            + entry.width() + "x" + entry.depth() + ", схема " + level.scheme() + " " + sx + "x" + sz
                            + "; все уровни обязаны стоять на одном участке");
                }
            }
        }
        assertEquals(
                List.of(),
                bad,
                String.join("\n", bad)
                        + "; иначе участок и схема разойдутся, дом уровня 4 выйдет за участок, и стройка встанет"
                        + " на первом же уровне — проверь размеры и перегенерируй схемы. При смене этого теста"
                        + " проверь это утверждение.");
    }

    @Test
    void sharedCatalogs_RepeatedLots_KeepSameSize() {
        Map<String, String> sizes = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (TownEntry entry : TownRules.load().buildings()) {
            if ("mine".equals(entry.id())) {
                continue;
            }
            StageCatalog.catalog(entry.id()).ifPresent(building -> {
                String size = entry.width() + "x" + entry.depth();
                String previous = sizes.putIfAbsent(building.id(), size);
                if (previous != null && !previous.equals(size)) {
                    bad.add("каталог '" + building.id() + "': лоты с разными участками " + previous + " и " + size);
                }
            });
        }
        assertEquals(
                List.of(),
                bad,
                String.join("\n", bad)
                        + "; иначе участок и схема разойдутся, дом уровня 4 выйдет за участок, и стройка встанет"
                        + " на первом же уровне — проверь размеры и перегенерируй схемы. При смене этого теста"
                        + " проверь это утверждение.");
    }

    @Test
    void townLots_EveryLot_HasCatalogBuilding() {
        List<String> unknown = new ArrayList<>();
        for (TownEntry entry : TownRules.load().buildings()) {
            if (!"mine".equals(entry.id())
                    && !"yard".equals(entry.id())
                    && StageCatalog.catalog(entry.id()).isEmpty()) {
                unknown.add(entry.id());
            }
        }
        assertTrue(
                unknown.isEmpty(),
                "лоты без каталога схем: " + unknown
                        + "; участок размечается, а строить нечего — иначе постройка не встанет и этап не закроется."
                        + " При смене townLots_EveryLot_HasCatalogBuilding проверь это утверждение.");
    }
}
