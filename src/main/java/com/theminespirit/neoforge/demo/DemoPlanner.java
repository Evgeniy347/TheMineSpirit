package com.theminespirit.neoforge.demo;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.BlockItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

/** Сборка демо-постройки из библиотеки пресетов: раскладка, материалы сундука и блоки без предмета. */
final class DemoPlanner {
    private DemoPlanner() {}

    static DemoPlan plan(
            DemoBuildings.Building building,
            int level,
            BlockPos anchor,
            int lookDx,
            int lookDz,
            BuildRotation rotation) {
        DemoBuildings.Level chosen = building.level(level);
        Preset preset = preset(chosen);
        Schematic schematic = preset.schematic();
        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(anchor.getX(), anchor.getY(), anchor.getZ()),
                new DemoPlacement.Look(lookDx, lookDz),
                DemoPlacement.Door.of(preset),
                new DemoPlacement.Size(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()),
                rotation);
        return plan(chosen, placement);
    }

    static Preset preset(DemoBuildings.Level chosen) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(chosen.scheme())
                .orElseThrow(() -> new IllegalArgumentException("Постройка '" + chosen.scheme()
                        + "' не найдена в библиотеке пресетов: проверьте presets.json и файл "
                        + chosen.scheme() + ".txt (/spirit tables)"));
    }

    // Участок уже разложен (витрина city раскладывает улицу сама): сундук, блоки без предмета и материалы.
    static DemoPlan plan(DemoBuildings.Level chosen, DemoPlacement placement) {
        Preset preset = preset(chosen);
        Schematic schematic = preset.schematic();
        String name = preset.name();
        Map<String, Integer> materials = preset.materials();
        List<DemoPlan.Placement> preplaced = new ArrayList<>();
        List<Schematic.PlacedEntry> entries = schematic.entries(placement.origin(), placement.rotation());
        for (Schematic.PlacedEntry placed : entries) {
            if (!BlockItems.hasBlockItem(placed.block())) {
                preplaced.add(new DemoPlan.Placement(placed.pos(), placed.block()));
            }
        }
        Map<String, Integer> bag = new TreeMap<>();
        for (Map.Entry<String, Integer> material : materials.entrySet()) {
            if (BlockItems.hasBlockItem(material.getKey())) {
                bag.merge(material.getKey(), material.getValue(), Integer::sum);
            }
        }
        return new DemoPlan(name, chosen.scheme(), placement, bag, preplaced, entries);
    }
}
