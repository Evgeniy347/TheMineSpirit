package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.EarthCover;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

final class SchematicSite {
    private static final Set<String> DERIVED = Set.of("shape");
    final Level level;
    final BuildRotation rotation;
    final Schematic schematic;
    final @Nullable Schematic previous;
    final BlockCoord origin;
    final List<Schematic.PlacedEntry> entries;
    final List<Schematic.PlacedEntry> primaries;
    final List<BlockCoord> air;
    final Set<BlockCoord> schemeCells = new HashSet<>();
    final LevelSafetyView safety;
    final int minX;
    final int maxX;
    final int minZ;
    final int maxZ;
    final int minY;
    final int maxY;
    private final Map<Schematic.PlacedEntry, BlockState> wantedStates = new HashMap<>();
    private final SchematicParts parts;
    private final SchematicStanding standing;
    private final SchematicClearance clearance;

    SchematicSite(Level level, Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        this(level, schematic, null, origin, rotation);
    }

    SchematicSite(
            Level level, Schematic schematic, @Nullable Schematic previous, BlockCoord origin, BuildRotation rotation) {
        this.level = level;
        this.rotation = rotation;
        this.schematic = schematic;
        this.previous = previous;
        this.origin = origin;
        this.entries = schematic.entries(origin, rotation).stream()
                .filter(entry -> SchematicActions.buildEntry(entry.block(), BlockItems.hasBlockItem(entry.block())))
                .toList();
        List<Schematic.PlacedEntry> firstParts = new ArrayList<>();
        for (Schematic.PlacedEntry entry : entries) {
            schemeCells.add(entry.pos());
            if (!SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                firstParts.add(entry);
            }
        }
        this.primaries = List.copyOf(firstParts);
        this.parts = new SchematicParts(entries);
        this.air = schematic.airCells(origin, rotation);
        this.safety = new LevelSafetyView(level);
        this.standing = new SchematicStanding(this);
        this.clearance = new SchematicClearance(this);
        this.minX = origin.x();
        this.minZ = origin.z();
        this.maxX = origin.x() + rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ()) - 1;
        this.maxZ = origin.z() + rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ()) - 1;
        this.minY = origin.y();
        this.maxY = origin.y() + schematic.sizeY() - 1;
    }

    Schematic schematic() {
        return schematic;
    }

    BlockCoord origin() {
        return origin;
    }

    BuildRotation rotation() {
        return rotation;
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

    Set<BlockCoord> standBlockers() {
        return standing.standBlockers();
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
        Schematic.PlacedEntry second = parts.secondOf(first);
        return second == null || matches(second);
    }

    boolean matches(Schematic.PlacedEntry entry) {
        BlockState current = level.getBlockState(toPos(entry.pos()));
        if (SchematicBlocks.isFloor(entry.block())) {
            return SchematicBlocks.isFloorClass(BlockStates.idOf(current));
        }
        BlockState wanted = wanted(entry);
        boolean block = current.is(wanted.getBlock())
                || WoodSpecies.sameRole(BlockStates.idOf(current), BlockStates.idOf(wanted))
                || EarthCover.covers(BlockStates.idOf(current), BlockStates.idOf(wanted));
        if (block && sameSpecifiedState(current, wanted, entry.state())) {
            return true;
        }
        if (!SchematicActions.isWalkway(entry.block()) || !current.isAir()) {
            return false;
        }
        return SchematicGround.walkwayPlaced(
                entry.block(),
                BlockStates.idOf(current),
                BlockStates.idOf(level.getBlockState(toPos(entry.pos()).below())),
                true);
    }

    boolean rightBlockWrongState(Schematic.PlacedEntry entry) {
        BlockState current = level.getBlockState(toPos(entry.pos()));
        BlockState wanted = wanted(entry);
        return current.is(wanted.getBlock()) && !sameSpecifiedState(current, wanted, entry.state());
    }

    List<Schematic.PlacedEntry> missing() {
        return clearance.missing();
    }

    List<BlockCoord> toClear() {
        return clearance.toClear();
    }

    boolean freeForPlacing(Schematic.PlacedEntry entry) {
        BlockState current = level.getBlockState(toPos(entry.pos()));
        return SchematicActions.isActionPlaced(entry.block()) || current.isAir() || current.canBeReplaced();
    }

    void repairState(Schematic.PlacedEntry entry, SpiritEntity spirit) {
        BlockPos target = toPos(entry.pos());
        BlockState wanted = wanted(entry);
        SpiritBlocks.place((ServerLevel) spirit.level(), target, wanted, spirit, "REPAIR_SCHEMA");
        if (!wanted.hasProperty(BlockStateProperties.BED_PART)
                && !wanted.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !BlockPlacer.pairedChestHalf(wanted)) {
            BlockState updated = Block.updateFromNeighbourShapes(wanted, level, target);
            if (!updated.equals(wanted)) {
                SpiritBlocks.place((ServerLevel) spirit.level(), target, updated, spirit, "REPAIR_SCHEMA");
            }
        }
    }

    boolean supported(Schematic.PlacedEntry entry) {
        return SchematicActions.isFluidPlaced(entry.block())
                || PlaceSupport.hasSupport(level, toPos(entry.pos()), wanted(entry));
    }

    boolean standable(BlockCoord feet) {
        return standing.standable(feet);
    }

    BuildBox networkBox(int ring) {
        return standing.networkBox(ring);
    }

    List<BlockCoord> doorCells() {
        return standing.doorCells();
    }

    boolean onRoof(BlockCoord stand) {
        return standing.onRoof(stand);
    }

    boolean outsideFootprint(BlockCoord feet) {
        return standing.outsideFootprint(feet);
    }

    boolean overlaps(Schematic.PlacedEntry entry, AABB body) {
        return standing.overlaps(entry, body);
    }

    boolean keepsRetreat(BlockCoord feet, List<Schematic.PlacedEntry> entries) {
        return standing.keepsRetreat(feet, entries);
    }

    Optional<Set<BlockCoord>> walkable(BlockCoord feet, int margin) {
        return standing.walkable(feet, margin);
    }

    // mc: форму ступеньки (shape) игра выводит из соседей; пока соседней ступеньки крыльца нет,
    // угловая — прямая.
    // Стройка её не чинит (иначе починка и пересчёт игры зацикливаются), итог сверяет аудит, когда
    // соседи стоят.
    private static boolean sameSpecifiedState(BlockState current, BlockState wanted, Map<String, String> specified) {
        for (String name : specified.keySet()) {
            if (DERIVED.contains(name)) {
                continue;
            }
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
