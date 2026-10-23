package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.RetreatPath;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
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
import net.minecraft.core.Direction;
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

/**
 * Площадка стройки по схеме в мире: какие клетки ещё не совпадают со схемой, какие надо очистить (обязательный
 * воздух и чужой блок в клетке схемы), где можно стоять и держится ли блок. Решения «что ставить» принимает
 * {@link ReachPlanner}; площадка только читает мир.
 */
final class SchematicSite {
    private static final double FLAT_HEIGHT = 0.1;
    // Ниже — пол первого этажа и крыльцо: поверхность, но не крыша (как BuildPlanner).
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

    // Клетки схемы, где стоянки нет: блок со столкновением выше ковра. В клетке будущего ковра, порога, двери дух
    // стоять может, иначе в устланном коврами зале у пола нет ни одной стоянки (усадьба, 20.10).
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
            if (entry.optional() && level.getBlockState(toPos(entry.pos())).isAir()) {
                continue;
            }
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
            if (entry.optional()) {
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

    // Зона стройки для сети тропинок: габарит схемы с кольцом вокруг (стоянки снаружи) и запасом по высоте.
    BuildBox networkBox(int ring) {
        return BuildBox.of(minX - ring, minY - 1, minZ - ring, maxX + ring, maxY + 2, maxZ + ring);
    }

    // Проёмы дверей схемы — начало сети стройки, как будто двери уже стоят (QUESTIONS-3, ответ 2).
    List<BlockCoord> doorCells() {
        List<BlockCoord> doors = new ArrayList<>();
        for (Schematic.PlacedEntry entry : primaries) {
            if (wanted(entry).getBlock() instanceof DoorBlock) {
                doors.add(entry.pos());
            }
        }
        return doors;
    }

    // На крыше — стоянка внутри габарита выше пола первого этажа, над которой по схеме нет твёрдого блока.
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

    // Блок со столкновением, чья клетка задевает тело духа: поставленный, он душит духа в стене.
    boolean overlaps(Schematic.PlacedEntry entry, AABB body) {
        BlockPos pos = toPos(entry.pos());
        return body.intersects(new AABB(pos))
                && !wanted(entry).getCollisionShape(level, pos).isEmpty();
    }

    // Правило отхода (владелец 20.10): после установки от ног духа остаётся путь наружу. Двери — проход (стройка
    // ставит их последним шагом снаружи), блоки без столкновения (ковёр, факел) путь не перекрывают.
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

    // Клетки, куда дух дойдёт пешком от ног в пределах площадки с запасом; пусто — обход слишком велик.
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
