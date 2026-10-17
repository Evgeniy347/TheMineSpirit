package com.theminespirit.neoforge.demo;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.SchematicResources;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

/** Сборка демо-постройки из библиотеки пресетов: раскладка, материалы сундука и блоки без предмета. */
final class DemoPlanner {
    private DemoPlanner() {
    }

    static DemoPlan plan(DemoBuildings.Building building, int level, BlockPos anchor, int lookDx, int lookDz,
            BuildRotation rotation) {
        DemoBuildings.Level chosen = building.level(level);
        Optional<Preset> found = DataTables.INSTANCE.presets().current().byId(chosen.scheme());
        Schematic schematic;
        BlockCoord entry;
        String name;
        Map<String, Integer> materials;
        if (found.isPresent()) {
            Preset preset = found.orElseThrow();
            schematic = preset.schematic();
            entry = preset.entry();
            name = preset.name();
            materials = preset.materials();
        } else {
            schematic = SchematicResources.load(chosen.scheme());
            entry = new BlockCoord(schematic.sizeX() / 2, 0, 0);
            name = building.name();
            materials = PresetLibrary.countMaterials(schematic);
        }
        DemoPlacement placement = DemoPlacement.compute(anchor.getX(), anchor.getY(), anchor.getZ(), lookDx, lookDz,
                entry.x(), entry.z(), schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), rotation);
        List<DemoPlan.Placement> preplaced = new ArrayList<>();
        for (Schematic.PlacedEntry placed : schematic.entries(placement.origin(), placement.rotation())) {
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
        return new DemoPlan(name, chosen.scheme(), placement, bag, preplaced);
    }
}
