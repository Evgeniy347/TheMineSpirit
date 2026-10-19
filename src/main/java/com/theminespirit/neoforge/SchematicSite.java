package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Площадка стройки по схеме в мире: какие клетки ещё не совпадают со схемой, какие надо очистить (обязательный
 * воздух и чужой блок в клетке схемы), где можно стоять и держится ли блок. Решения «что ставить» принимает
 * {@link ReachPlanner}; площадка только читает мир.
 */
final class SchematicSite {
    private final Level level;
    private final BuildRotation rotation;
    private final List<Schematic.PlacedEntry> entries;
    private final List<Schematic.PlacedEntry> primaries;
    private final List<BlockCoord> air;
    private final Set<BlockCoord> schemeCells = new HashSet<>();
    private final Map<Schematic.PlacedEntry, BlockState> wantedStates = new HashMap<>();
    private final Map<BlockCoord, Schematic.PlacedEntry> secondParts = new HashMap<>();
    private final LevelSafetyView safety;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    SchematicSite(Level level, Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        this.level = level;
        this.rotation = rotation;
        this.entries = schematic.entries(origin, rotation).stream()
                .filter(entry -> BlockItems.hasBlockItem(entry.block()))
                .toList();
        List<Schematic.PlacedEntry> firstParts = new ArrayList<>();
        for (Schematic.PlacedEntry entry : entries) {
            schemeCells.add(entry.pos());
            if (!SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                firstParts.add(entry);
            }
        }
        this.primaries = List.copyOf(firstParts);
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new HashMap<>();
        for (Schematic.PlacedEntry entry : entries) {
            byCell.put(entry.pos(), entry);
        }
        for (Schematic.PlacedEntry entry : entries) {
            if (SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                firstPartOf(entry, byCell).ifPresent(first -> secondParts.put(first.pos(), entry));
            }
        }
        this.air = schematic.airCells(origin, rotation);
        this.safety = new LevelSafetyView(level);
        this.minX = origin.x();
        this.minZ = origin.z();
        this.maxX = origin.x() + rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ()) - 1;
        this.maxZ = origin.z() + rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ()) - 1;
    }

    List<Schematic.PlacedEntry> entries() {
        return entries;
    }

    List<Schematic.PlacedEntry> primaries() {
        return primaries;
    }

    Set<BlockCoord> schemeCells() {
        return schemeCells;
    }

    BlockState wanted(Schematic.PlacedEntry entry) {
        return wantedStates.computeIfAbsent(entry, this::resolveWanted);
    }

    private BlockState resolveWanted(Schematic.PlacedEntry entry) {
        BlockState state = BlockStates.stateFor(entry.block(), entry.state());
        if (rotation == BuildRotation.NONE) {
            return state;
        }
        return state.rotate(level, toPos(entry.pos()), mcRotation(rotation));
    }

    boolean secondPartMatches(Schematic.PlacedEntry first) {
        Schematic.PlacedEntry second = secondParts.get(first.pos());
        return second == null || matches(second);
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

    boolean matches(Schematic.PlacedEntry entry) {
        BlockState current = level.getBlockState(toPos(entry.pos()));
        BlockState wanted = wanted(entry);
        return current.is(wanted.getBlock()) && sameSpecifiedState(current, wanted, entry.state());
    }

    boolean rightBlockWrongState(Schematic.PlacedEntry entry) {
        BlockState current = level.getBlockState(toPos(entry.pos()));
        BlockState wanted = wanted(entry);
        return current.is(wanted.getBlock()) && !sameSpecifiedState(current, wanted, entry.state());
    }

    List<Schematic.PlacedEntry> missing() {
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        for (Schematic.PlacedEntry entry : entries) {
            if (!matches(entry)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    List<BlockCoord> toClear() {
        List<BlockCoord> cells = new ArrayList<>();
        for (BlockCoord cell : air) {
            BlockState current = level.getBlockState(toPos(cell));
            if (!current.isAir() && current.getFluidState().isEmpty()) {
                cells.add(cell);
            }
        }
        for (Schematic.PlacedEntry entry : entries) {
            BlockState current = level.getBlockState(toPos(entry.pos()));
            if (current.isAir()
                    || current.canBeReplaced()
                    || current.is(wanted(entry).getBlock())) {
                continue;
            }
            cells.add(entry.pos());
        }
        cells.sort(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()));
        return cells;
    }

    boolean freeForPlacing(Schematic.PlacedEntry entry) {
        BlockState current = level.getBlockState(toPos(entry.pos()));
        return current.isAir() || current.canBeReplaced();
    }

    void repairState(Schematic.PlacedEntry entry) {
        BlockPos target = toPos(entry.pos());
        BlockState wanted = wanted(entry);
        level.setBlock(target, wanted, Block.UPDATE_ALL);
        if (!wanted.hasProperty(BlockStateProperties.BED_PART)
                && !wanted.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !BlockPlacer.pairedChestHalf(wanted)) {
            BlockState updated = Block.updateFromNeighbourShapes(wanted, level, target);
            if (!updated.equals(wanted)) {
                level.setBlock(target, updated, Block.UPDATE_ALL);
            }
        }
    }

    boolean supported(Schematic.PlacedEntry entry) {
        return BlockPlacer.hasSupport(level, toPos(entry.pos()), wanted(entry));
    }

    boolean standable(BlockCoord feet) {
        return MiningSafety.canStand(feet, safety);
    }

    boolean outsideFootprint(BlockCoord feet) {
        return feet.x() < minX || feet.x() > maxX || feet.z() < minZ || feet.z() > maxZ;
    }

    private static boolean sameSpecifiedState(BlockState current, BlockState wanted, Map<String, String> specified) {
        for (String name : specified.keySet()) {
            Property<?> property = current.getBlock().getStateDefinition().getProperty(name);
            if (property == null || !current.getValue(property).equals(wanted.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    static Rotation mcRotation(BuildRotation rotation) {
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        };
    }
}
