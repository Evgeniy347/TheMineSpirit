package com.theminespirit.neoforge;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class SchematicParts {
    private final Map<BlockCoord, Schematic.PlacedEntry> secondParts = new HashMap<>();

    SchematicParts(List<Schematic.PlacedEntry> entries) {
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new HashMap<>();
        for (Schematic.PlacedEntry entry : entries) {
            byCell.put(entry.pos(), entry);
        }
        for (Schematic.PlacedEntry entry : entries) {
            if (SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                firstPartOf(entry, byCell).ifPresent(first -> secondParts.put(first.pos(), entry));
            }
        }
    }

    Schematic.@Nullable PlacedEntry secondOf(Schematic.PlacedEntry first) {
        return secondParts.get(first.pos());
    }

    private static Optional<Schematic.PlacedEntry> firstPartOf(
            Schematic.PlacedEntry second, Map<BlockCoord, Schematic.PlacedEntry> byCell) {
        BlockCoord at = second.pos();
        List<BlockCoord> neighbours = List.of(
                new BlockCoord(at.x(), at.y() - 1, at.z()),
                new BlockCoord(at.x() + 1, at.y(), at.z()),
                new BlockCoord(at.x() - 1, at.y(), at.z()),
                new BlockCoord(at.x(), at.y(), at.z() + 1),
                new BlockCoord(at.x(), at.y(), at.z() - 1));
        for (BlockCoord cell : neighbours) {
            Schematic.PlacedEntry candidate = byCell.get(cell);
            if (candidate != null
                    && candidate.block().equals(second.block())
                    && !SchematicBlocks.isSecondaryPart(candidate.block(), candidate.state())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
