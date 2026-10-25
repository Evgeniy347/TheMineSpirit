package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SchematicResources;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Приёмка этапа 1 по миру, а не по журналу доски: участок ровный, ограда по схеме и непроходима, хижина, спуск в
 * шахту и факелы по плану раскладки. Каждая проверка возвращает список расхождений с координатами.
 */
final class StageOneAudit {
    private static final int SHOWN = 6;
    private static final int SCAN = 12;

    private StageOneAudit() {}

    static List<String> problems(ServerLevel level, BlockCoord anchor, String wood) {
        List<String> problems = new ArrayList<>();
        level(level, anchor, problems);
        building(level, BoardCase.BUILD_FENCE, anchor, wood, problems);
        fenceSides(level, anchor, problems);
        building(level, BoardCase.BUILD_HOUSE, anchor, wood, problems);
        mine(level, anchor, problems);
        torches(level, anchor, problems);
        return problems;
    }

    private static void level(ServerLevel level, BlockCoord anchor, List<String> problems) {
        List<String> off = new ArrayList<>();
        int half = SettlementLayout.LEVEL_HALF;
        for (int x = anchor.x() - half; x <= anchor.x() + half; x++) {
            for (int z = anchor.z() - half; z <= anchor.z() + half; z++) {
                int feet = feet(level, anchor, x, z);
                if (!SettlementLayout.mineColumn(anchor, x, z)
                        && Math.abs(feet - anchor.y()) > PlotLeveling.TOLERANCE) {
                    // Что стоит под ногами — сразу видно, листва это, верстак или земля (сцена 56).
                    BlockPos top = new BlockPos(x, feet - 1, z);
                    off.add("(" + x + ", " + z + ") ноги " + feet + " на "
                            + BuiltInRegistries.BLOCK
                                    .getKey(level.getBlockState(top).getBlock())
                                    .getPath());
                }
            }
        }
        report("участок не ровный на y=" + anchor.y(), off, problems);
    }

    // Ограда непроходима: земля вплотную к ней изнутри и снаружи не выше уровня участка — с уступа в блок забор
    // высотой 1,5 перешагивается прыжком.
    private static void fenceSides(ServerLevel level, BlockCoord anchor, List<String> problems) {
        List<String> steps = new ArrayList<>();
        int ring = SettlementLayout.HALF;
        for (int offset : new int[] {ring - 1, ring + 1}) {
            for (int along = -offset; along <= offset; along++) {
                int[][] cells = {{along, -offset}, {along, offset}, {-offset, along}, {offset, along}};
                for (int[] cell : cells) {
                    int x = anchor.x() + cell[0];
                    int z = anchor.z() + cell[1];
                    int feet = feet(level, anchor, x, z);
                    if (!SettlementLayout.mineColumn(anchor, x, z) && feet > anchor.y()) {
                        steps.add("(" + x + ", " + z + ") ноги " + feet);
                    }
                }
            }
        }
        report("уступ у ограды — забор перешагивается", steps, problems);
    }

    private static void building(
            ServerLevel level, BoardCase step, BlockCoord anchor, String wood, List<String> problems) {
        TicketTarget target = TicketTarget.at(anchor);
        if (!WoodSpecies.OAK.equals(wood)) {
            target = target.withWood(wood);
        }
        List<String> missing = new ArrayList<>();
        int[] total = {0};
        collect(level, TicketPlans.plan(step, 1, target), wood, missing, total);
        if (total[0] == 0) {
            problems.add(step + ": в плане нет ни одного блока — сверять нечего");
        }
        report(step + " не по схеме: нет " + missing.size() + " из " + total[0], missing, problems);
    }

    private static void collect(ServerLevel level, TaskNode plan, String wood, List<String> missing, int[] total) {
        if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(node -> collect(level, node, wood, missing, total));
            return;
        }
        if (!(plan instanceof ScriptCall call)
                || !(call.params().get("x") instanceof Integer x)
                || !(call.params().get("y") instanceof Integer y)
                || !(call.params().get("z") instanceof Integer z)) {
            return;
        }
        if ("place_block".equals(call.script()) && call.params().get("block") instanceof String block) {
            expect(level, new BlockPos(x, y, z), WoodSpecies.swap(block, wood), missing, total);
        }
        if ("build_schematic".equals(call.script())
                && !call.params().containsKey("rotation")
                && call.params().get("file") instanceof String file) {
            for (Schematic.Entry entry : SchematicResources.load(file).blocks()) {
                if (!entry.optional() && !SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                    BlockPos pos = new BlockPos(x + entry.dx(), y + entry.dy(), z + entry.dz());
                    expect(level, pos, WoodSpecies.swap(entry.block(), wood), missing, total);
                }
            }
        }
    }

    private static void expect(ServerLevel level, BlockPos pos, String block, List<String> missing, int[] total) {
        total[0]++;
        String want = bare(block);
        String actual = bare(BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock())
                .toString());
        boolean torch = "torch".equals(want) && "wall_torch".equals(actual);
        if (!actual.equals(want) && !torch && !WoodSpecies.sameRole(actual, want)) {
            missing.add(pos.toShortString() + " " + want + "≠" + actual);
        }
    }

    private static void mine(ServerLevel level, BlockCoord anchor, List<String> problems) {
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(TicketPlans.MINE_DESCENT_SCHEME);
        if (preset.isEmpty()) {
            problems.add("схема спуска " + TicketPlans.MINE_DESCENT_SCHEME + " не загружена");
            return;
        }
        MineLayout layout = MineLayout.place(
                preset.get().schematic(),
                preset.get().entry(),
                TicketPlans.mineDescentOrigin(anchor),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE);
        List<String> wrong = new ArrayList<>();
        for (BlockCoord cell : layout.air()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                wrong.add(pos.toShortString() + " проход завален");
            }
        }
        for (BlockCoord cell : layout.floors()) {
            BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (!SchematicBlocks.isFloorClass(id)) {
                wrong.add(cell + " пол/стенка " + id);
            }
        }
        report("спуск в шахту не по схеме", wrong, problems);
    }

    private static void torches(ServerLevel level, BlockCoord anchor, List<String> problems) {
        List<String> dark = new ArrayList<>();
        for (BlockCoord cell : SettlementLayout.lighting(anchor)) {
            BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
            if (!state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH)) {
                dark.add(cell + " " + state.getBlock());
            }
        }
        report("факелов освещения нет", dark, problems);
    }

    // Уровень ног колонки: над верхним блоком земли (постройки, деревья, факелы землёй не считаются).
    static int feet(ServerLevel level, BlockCoord anchor, int x, int z) {
        for (int y = anchor.y() + SCAN; y > anchor.y() - SCAN; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.is(BlockTags.DIRT)
                    || state.is(BlockTags.BASE_STONE_OVERWORLD)
                    || state.is(BlockTags.SAND)
                    || state.is(Blocks.GRAVEL)
                    || state.is(Blocks.COBBLESTONE)
                    || !state.getFluidState().isEmpty()) {
                return y + 1;
            }
        }
        return anchor.y() - SCAN;
    }

    private static void report(String what, List<String> cells, List<String> problems) {
        if (!cells.isEmpty()) {
            problems.add(what + " (" + cells.size() + "): " + cells.subList(0, Math.min(SHOWN, cells.size())));
        }
    }

    private static String bare(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
