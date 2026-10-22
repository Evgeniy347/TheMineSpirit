package com.theminespirit.gametest;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Сверка стройки по схеме: мир в клетках схемы, отсутствие лишних блоков в районе стройки,
 * ровно одна постановка на клетку и ноль пропусков. Возвращает список расхождений; assert'ов внутри нет.
 */
public final class BuildAudit {
    public record Before(Map<BlockCoord, BlockState> states) {
        public Before {
            states = Map.copyOf(states);
        }
    }

    public record Result(List<String> problems) {
        public Result {
            problems = List.copyOf(problems);
        }

        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private BuildAudit() {}

    public static Before snapshot(
            Level level, Schematic schematic, BlockCoord origin, BuildRotation rotation, int margin) {
        Map<BlockCoord, BlockState> states = new HashMap<>();
        BlockCoord min = new BlockCoord(origin.x() - margin, origin.y() - margin, origin.z() - margin);
        BlockCoord max = new BlockCoord(
                origin.x() + rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ()) - 1 + margin,
                origin.y() + schematic.sizeY() - 1 + margin,
                origin.z() + rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ()) - 1 + margin);
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos)) {
                        states.put(new BlockCoord(x, y, z), level.getBlockState(pos));
                    }
                }
            }
        }
        return new Before(states);
    }

    public static Result audit(
            Level level, Schematic schematic, BlockCoord origin, BuildRotation rotation, Before before, int skipped) {
        List<String> problems = new ArrayList<>();
        List<Schematic.PlacedEntry> entries = schematic.entries(origin, rotation);
        Set<BlockCoord> expectedCells = new HashSet<>();
        for (Schematic.PlacedEntry entry : entries) {
            expectedCells.add(entry.pos());
        }
        for (Schematic.PlacedEntry entry : entries) {
            if (!entry.optional()) {
                auditCell(level, entry, rotation, problems);
            }
        }
        for (Map.Entry<BlockCoord, BlockState> snapshot : before.states().entrySet()) {
            BlockCoord cell = snapshot.getKey();
            BlockState current = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
            if (!current.equals(snapshot.getValue()) && !expectedCells.contains(cell)) {
                problems.add("лишний блок духа в " + cell + ": был "
                        + snapshot.getValue().getBlock() + ", стал " + current.getBlock());
            }
        }
        for (Schematic.PlacedEntry entry : entries) {
            BlockCoord cell = entry.pos();
            if (!entry.optional()
                    && BlockItems.hasBlockItem(entry.block())
                    && !wasAlready(entry, before)
                    && !(level instanceof ServerLevel server
                            && PlacedBlocksData.get(server)
                                    .isSpiritMarked(new BlockPos(cell.x(), cell.y(), cell.z())))) {
                problems.add("клетка " + cell + " не помечена духом: постановка не учтена");
            }
        }
        if (skipped != 0) {
            problems.add("скрипт пропустил " + skipped + " клеток");
        }
        return new Result(problems);
    }

    private static void auditCell(
            Level level, Schematic.PlacedEntry entry, BuildRotation rotation, List<String> problems) {
        BlockPos pos =
                new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
        BlockState current = level.getBlockState(pos);
        if (SchematicBlocks.isFloor(entry.block())) {
            if (!SchematicBlocks.isFloorClass(BlockStates.idOf(current))) {
                problems.add("клетка " + entry.pos() + " не совпадает со схемой: ожидался пол @floor (любой "
                        + "дешёвый твёрдый блок), стоит " + current.getBlock());
            }
            return;
        }
        if (!current.is(BlockStates.blockFor(entry.block()))) {
            problems.add("клетка " + entry.pos() + " не совпадает со схемой: ожидался " + entry.block() + ", стоит "
                    + current.getBlock());
            return;
        }
        if (entry.state().isEmpty()) {
            return;
        }
        BlockState expected = BlockStates.stateFor(entry.block(), entry.state());
        if (rotation != BuildRotation.NONE) {
            expected = expected.rotate(level, pos, mcRotation(rotation));
        }
        for (String name : entry.state().keySet()) {
            Property<?> property = current.getBlock().getStateDefinition().getProperty(name);
            if (property == null || !current.getValue(property).equals(expected.getValue(property))) {
                problems.add("клетка " + entry.pos() + " не совпадает по состоянию: свойство " + name
                        + " ожидалось " + (property == null ? "нет свойства" : expected.getValue(property))
                        + ", стоит " + (property == null ? "нет свойства" : current.getValue(property)));
            }
        }
    }

    private static boolean wasAlready(Schematic.PlacedEntry entry, Before before) {
        if (SchematicBlocks.isFloor(entry.block())) {
            BlockState old = before.states().get(entry.pos());
            return old != null && SchematicBlocks.isFloorClass(BlockStates.idOf(old));
        }
        BlockState expected = BlockStates.stateFor(entry.block(), entry.state());
        BlockState old = before.states().get(entry.pos());
        return old != null && old.is(expected.getBlock());
    }

    private static Rotation mcRotation(BuildRotation rotation) {
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        };
    }
}
