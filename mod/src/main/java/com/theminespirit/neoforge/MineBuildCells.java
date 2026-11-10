package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.MineCellPlan;
import com.theminespirit.core.mining.MineCellStands;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.MineDetour;
import com.theminespirit.core.mining.MineFloor;
import com.theminespirit.core.mining.MineFluids;
import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.mining.MinePlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class MineBuildCells {
    interface Host {
        void failed(String reason);

        void aim();

        void pick();

        void breaking();
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> FLOOR_MATERIALS =
            List.of("minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:stone", "minecraft:dirt");

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockPlacer placer;
    private final BlockBreaker breaker;
    private final MineAimStep aim;
    private final List<MineColumn> plan;
    private final Map<MineColumn, MineColumn> parents;
    private final MineCells cells;
    private final Set<MineColumn> closed;
    private final Set<BlockCoord> openCells;
    private final Host host;
    private final Set<BlockCoord> leftOres = new HashSet<>();

    private int floors;
    private int plugs;
    private int detours;

    record Setup(SpiritEntity spirit, Level level, BlockPlacer placer, BlockBreaker breaker, MineAimStep aim) {}

    record Grid(
            List<MineColumn> plan,
            Map<MineColumn, MineColumn> parents,
            MineCells cells,
            Set<MineColumn> closed,
            Set<BlockCoord> openCells,
            Host host) {}

    MineBuildCells(Setup setup, Grid grid) {
        this.spirit = setup.spirit();
        this.level = setup.level();
        this.placer = setup.placer();
        this.breaker = setup.breaker();
        this.aim = setup.aim();
        this.plan = grid.plan();
        this.parents = grid.parents();
        this.cells = grid.cells();
        this.closed = grid.closed();
        this.openCells = grid.openCells();
        this.host = grid.host();
    }

    Set<BlockCoord> leftOres() {
        return leftOres;
    }

    int floors() {
        return floors;
    }

    int plugs() {
        return plugs;
    }

    int detours() {
        return detours;
    }

    MineAimStep.Outcome aimTick() {
        return aim.tick();
    }

    String aimFailure() {
        return aim.failure();
    }

    void resetAim() {
        aim.reset();
    }

    boolean solidReady(BlockCoord floor, String what) {
        if (leftOres.contains(floor)) {
            return true;
        }
        BlockPos pos = toPos(floor);
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        MineFloor.Action action = MineFloor.decide(new MineFloor.Seen(
                BlockStates.idOf(state),
                state.isAir(),
                !state.getFluidState().isEmpty(),
                state.canBeReplaced(),
                below.isFaceSturdy(level, pos.below(), Direction.UP)));
        return switch (action) {
            case KEEP -> true;
            case PLACE -> {
                if (place(floor, what)) {
                    floors++;
                }
                yield false;
            }
            case REPLACE -> {
                startBreak(floor, OptionalInt.empty());
                yield false;
            }
            case ORE -> oreInFloor(floor, state);
        };
    }

    private boolean oreInFloor(BlockCoord floor, BlockState state) {
        MineOres.Decision decision = MineOres.decide(BlockStates.idOf(state), picks(state));
        if (decision instanceof MineOres.Mine mine) {
            startBreak(floor, mine.slot());
            return false;
        }
        remember(floor, state, ((MineOres.Leave) decision).reason());
        return true;
    }

    boolean cellReady(MineColumn column, BlockCoord cell) {
        if (cells.open(cell)) {
            return true;
        }
        List<BlockCoord> plugsNeeded = MineFluids.plugsBefore(cell, openCells, cells::fluid);
        if (!plugsNeeded.isEmpty()) {
            if (place(plugsNeeded.get(0), "пробка")) {
                plugs++;
            }
            return false;
        }
        BlockState state = level.getBlockState(toPos(cell));
        MineOres.Decision decision = MineOres.decide(BlockStates.idOf(state), picks(state));
        if (decision instanceof MineOres.Leave leave) {
            remember(cell, state, leave.reason());
            closeAndDetour(column);
            return false;
        }
        startBreak(cell, ((MineOres.Mine) decision).slot());
        return false;
    }

    private void closeAndDetour(MineColumn column) {
        closed.add(column);
        host.pick();
        MineColumn parent = parents.get(column);
        if (parent == null) {
            host.failed("руда в колонке входа " + column.floor() + ": обходить не от чего, вход перенести");
            return;
        }
        MineColumn forward = MinePlan.forward(parent, column, plan).orElse(null);
        Optional<List<MineColumn>> detour = MineDetour.around(parent, column, forward, this::undiggable);
        if (detour.isEmpty()) {
            host.failed("руда в " + column.floor() + " оставлена, но обход справа и слева тоже упирается в руду");
            return;
        }
        int at = plan.indexOf(column) + 1;
        for (MineColumn side : detour.get()) {
            cells.removeWalls(side.cells());
            if (!plan.contains(side)) {
                plan.add(at++, side);
            }
        }
        detours++;
        LOGGER.info(
                "Шахта: дух {} обходит руду в {} колонками {}", spirit.getStringUUID(), column.floor(), detour.get());
    }

    private boolean undiggable(BlockCoord cell) {
        BlockState state = level.getBlockState(toPos(cell));
        if (leftOres.contains(cell) || state.getDestroySpeed(level, toPos(cell)) < 0) {
            return true;
        }
        return MineOres.decide(BlockStates.idOf(state), picks(state)) instanceof MineOres.Leave;
    }

    private void remember(BlockCoord cell, BlockState state, String reason) {
        if (leftOres.add(cell) && level instanceof ServerLevel server) {
            BlockPos feet = spirit.blockPosition();
            MineOreLedger.get(server)
                    .remember(cell, BlockStates.idOf(state), new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
            LOGGER.info(
                    "Шахта: дух {} оставил {} в {}: {}", spirit.getStringUUID(), BlockStates.idOf(state), cell, reason);
        }
    }

    private boolean place(BlockCoord cell, String what) {
        String material = FLOOR_MATERIALS.stream()
                .filter(id -> BlockItems.countIn(spirit.getInventory(), id) > 0)
                .findFirst()
                .orElse(null);
        if (material == null) {
            host.failed("нет булыжника на " + what + " в " + cell + ": проход без него затопит или дух провалится");
            return false;
        }
        if (MineCellStands.occupies(new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()), cell)) {
            aimAt(cell, new MineCellPlan.Seen(aim.occupantText(cell), true, true, true));
            return false;
        }
        BlockPlacer.Begin begin = placer.beginWithoutSupport(toPos(cell), BlockStates.stateFor(material), material);
        if (begin instanceof BlockPlacer.Started) {
            return true;
        }
        if (begin instanceof BlockPlacer.Refused refused) {
            if (refused.reason() == BlockPlacer.RefuseReason.OCCUPIED) {
                String other = aim.occupantText(cell);
                if (other != null) {
                    aimAt(cell, new MineCellPlan.Seen(other, false, true, true));
                    return false;
                }
            }
            if (refused.reason() == BlockPlacer.RefuseReason.OUT_OF_REACH) {
                aimAt(cell, new MineCellPlan.Seen(aim.occupantText(cell), false, true, false));
                return false;
            }
            host.failed(what + " в " + cell + " не поставить: " + refused.message() + aim.context(cell));
            return false;
        }
        host.failed(
                what + " в " + cell + " не поставить: " + ((BlockPlacer.Protected) begin).reason() + aim.context(cell));
        return false;
    }

    void startBreak(BlockCoord cell, OptionalInt slot) {
        BlockPos pos = toPos(cell);
        BlockBreaker.Begin begin =
                slot.isPresent() ? breaker.beginWithSlot(pos, slot.getAsInt()) : breaker.beginExcavate(pos);
        if (begin instanceof BlockBreaker.Started) {
            host.breaking();
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                return;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH) {
                aimAt(cell, new MineCellPlan.Seen(aim.occupantText(cell), false, true, false));
                return;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.NO_SIGHT) {
                aimAt(cell, new MineCellPlan.Seen(aim.occupantText(cell), false, false, true));
                return;
            }
            host.failed("клетку шахты " + cell + " не выломать: " + refused.message() + aim.context(cell));
            return;
        }
        host.failed("клетку шахты " + cell + " не выломать: " + ((BlockBreaker.Protected) begin).reason()
                + aim.context(cell));
    }

    private void aimAt(BlockCoord cell, MineCellPlan.Seen seen) {
        MineAimStep.Begin begin = aim.begin(cell, seen);
        switch (begin.next()) {
            case READY -> {}
            case AIM -> host.aim();
            case REFUSE -> host.failed(begin.failure());
        }
    }

    private List<MineOres.Pick> picks(BlockState state) {
        return PickOptions.of(spirit, state);
    }

    private static BlockPos toPos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
