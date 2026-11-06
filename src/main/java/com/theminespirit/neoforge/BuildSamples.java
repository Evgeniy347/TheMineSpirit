package com.theminespirit.neoforge;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;

final class BuildSamples {
    private BuildSamples() {}

    static String of(List<Schematic.PlacedEntry> missing, List<BlockCoord> clear) {
        List<String> shown = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            if (shown.size() == 6) {
                shown.add("…");
                break;
            }
            shown.add(entry.block() + "@" + entry.pos());
        }
        for (BlockCoord cell : clear) {
            if (shown.size() >= 8) {
                break;
            }
            shown.add("воздух@" + cell);
        }
        return String.join(", ", shown);
    }
}
