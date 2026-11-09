package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.EarthCover;
import com.theminespirit.core.building.RetreatPath;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.building.SchematicUpgrade;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.forestry.TreeSpan;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

final class SchematicSite {
    private static final double FLAT_HEIGHT = 0.1;
    private static final int ROOF_MIN_HEIGHT = 3;
    private static final Set<String> DERIVED = Set.of("shape");
    private final Level level;
    private final BuildRotation rotation;
    private final Schematic schematic;
    private final BlockCoord origin;
    private final List<Schematic.PlacedEntry> entries;
    private final List<Schematic.PlacedEntry> primaries;
    private final List<BlockCoord> air;
    private final Set<BlockCoord> schemeCells = new HashSet<>();
    private @Nullable Set<BlockCoord> standBlockers;
    private final Map<Schematic.PlacedEntry, BlockState> wantedStates = new HashMap<>();
    private final Map<BlockCoord, Schematic.PlacedEntry> secondParts = new HashMap<>();
    private final LevelSafetyView safety;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int minY;
    private final int maxY;

    SchematicSite(Level level, Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        this.level = level;
        this.rotation = rotation;
        this.schematic = schematic;
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
        Set<BlockCoord> cached = standBlockers;
        if (cached == null) {
            Set<BlockCoord> cells = new HashSet<>();
            for (Schematic.PlacedEntry entry : entries) {
                BlockState state = wanted(entry);
                BlockPos pos = toPos(entry.pos());
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (!(state.getBlock() instanceof DoorBlock)
                        && !shape.isEmpty()
                        && shape.max(Direction.Axis.Y) > FLAT_HEIGHT) {
                    cells.add(entry.pos());
                }
            }
            cached = Set.copyOf(cells);
            standBlockers = cached;
        }
        return cached;
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
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        for (Schematic.PlacedEntry entry : entries) {
            if (!entry.optional() && !matches(entry)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    List<BlockCoord> toClear() {
        Set<BlockCoord> unique = new LinkedHashSet<>();
        for (BlockCoord cell : air) {
            BlockState current = level.getBlockState(toPos(cell));
            if (current.is(BlockTags.LOGS)) {
                unique.addAll(TreeSpan.of(cell, at -> SchematicWild.log(level, schemeCells, at)));
            } else if (!current.isAir() && current.getFluidState().isEmpty()) {
                unique.add(cell);
            }
        }
        List<BlockCoord> cells = SchematicWild.clearCells(level, unique, entries);
        for (Schematic.PlacedEntry entry : entries) {
            if (entry.optional() || SchematicActions.isActionPlaced(entry.block())) {
                continue;
            }
            BlockState current = level.getBlockState(toPos(entry.pos()));
            if (current.isAir()
                    || current.canBeReplaced()
                    || current.is(wanted(entry).getBlock())
                    || (SchematicBlocks.isFloor(entry.block())
                            && SchematicBlocks.isFloorClass(BlockStates.idOf(current)))) {
                continue;
            }
            cells.add(entry.pos());
        }
        addOldSchemeExtras(cells);
        cells.sort(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()));
        return cells;
    }

    private void addOldSchemeExtras(List<BlockCoord> cells) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        StageSite site = SettlementPlotStore.liveSite(server).orElse(null);
        if (site == null) {
            return;
        }
        PlanBuilding own = site.protectionBuilding(origin.x(), origin.y(), origin.z());
        if (own == PlanBuilding.NONE) {
            return;
        }
        PlacedBlocksData placed = PlacedBlocksData.get(server);
        List<BlockCoord> previous = new ArrayList<>();
        for (int y = minY; y <= maxY + StageSite.BUILDING_HEIGHT; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (site.protectionBuilding(x, y, z) != own) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (placed.isSpiritMarked(pos) && !level.getBlockState(pos).isAir()) {
                        previous.add(new BlockCoord(x, y, z));
                    }
                }
            }
        }
        cells.addAll(SchematicUpgrade.extraPositions(previous, schematic, origin, BuildRotation.NONE));
    }

    boolean freeForPlacing(Schematic.PlacedEntry entry) {
        BlockState current = level.getBlockState(toPos(entry.pos()));
        return SchematicActions.isActionPlaced(entry.block()) || current.isAir() || current.canBeReplaced();
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
        return SchematicActions.isFluidPlaced(entry.block())
                || BlockPlacer.hasSupport(level, toPos(entry.pos()), wanted(entry));
    }

    boolean standable(BlockCoord feet) {
        return MiningSafety.canStand(feet, safety);
    }

    BuildBox networkBox(int ring) {
        return BuildBox.of(minX - ring, minY - 1, minZ - ring, maxX + ring, maxY + 2, maxZ + ring);
    }

    List<BlockCoord> doorCells() {
        List<BlockCoord> doors = new ArrayList<>();
        for (Schematic.PlacedEntry entry : primaries) {
            if (wanted(entry).getBlock() instanceof DoorBlock) {
                doors.add(entry.pos());
            }
        }
        return doors;
    }

    boolean onRoof(BlockCoord stand) {
        if (outsideFootprint(stand) || stand.y() < minY + ROOF_MIN_HEIGHT) {
            return false;
        }
        for (int y = stand.y() + 2; y <= maxY; y++) {
            if (standBlockers().contains(new BlockCoord(stand.x(), y, stand.z()))) {
                return false;
            }
        }
        return true;
    }

    boolean outsideFootprint(BlockCoord feet) {
        return feet.x() < minX || feet.x() > maxX || feet.z() < minZ || feet.z() > maxZ;
    }

    boolean overlaps(Schematic.PlacedEntry entry, AABB body) {
        BlockPos pos = toPos(entry.pos());
        return body.intersects(new AABB(pos))
                && !wanted(entry).getCollisionShape(level, pos).isEmpty();
    }

    boolean keepsRetreat(BlockCoord feet, List<Schematic.PlacedEntry> entries) {
        Set<BlockCoord> placed = new HashSet<>();
        for (Schematic.PlacedEntry entry : entries) {
            BlockState state = wanted(entry);
            if (!(state.getBlock() instanceof DoorBlock)
                    && !state.getCollisionShape(level, toPos(entry.pos())).isEmpty()) {
                placed.add(entry.pos());
            }
        }
        return RetreatPath.allows(feet, placed, walkView(), this::outsideFootprint);
    }

    Optional<Set<BlockCoord>> walkable(BlockCoord feet, int margin) {
        return RetreatPath.walkable(
                feet,
                walkView(),
                cell -> cell.x() >= minX - margin
                        && cell.x() <= maxX + margin
                        && cell.z() >= minZ - margin
                        && cell.z() <= maxZ + margin);
    }

    private SafetyView walkView() {
        return cell ->
                level.getBlockState(toPos(cell)).getBlock() instanceof DoorBlock ? Cell.PASSABLE : safety.cellAt(cell);
    }

    // mc: форму ступеньки (shape) игра выводит из соседей; пока соседней ступеньки крыльца нет, угловая — прямая.
    // Стройка её не чинит (иначе починка и пересчёт игры зацикливаются), итог сверяет аудит, когда соседи стоят.
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
