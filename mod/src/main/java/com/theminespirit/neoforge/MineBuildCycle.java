package com.theminespirit.neoforge;

import com.theminespirit.core.board.StallWatch;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.MineDigOrder;
import com.theminespirit.core.mining.MinePlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

final class MineBuildCycle {
    interface Host {
        void failed(String reason);

        void succeeded();
    }

    private static final int SETTLE_TICKS = 5;
    private static final int MAX_ACTIONS_PER_COLUMN = 64;

    private enum Phase {
        PICK,
        WALK,
        DIG,
        AIM,
        SETTLE,
        TORCH,
        EXIT,
        DONE
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final MineFinish finish;
    private final List<MineColumn> plan;
    private final Set<MineColumn> open;
    private final Set<MineColumn> closed;
    private final Set<BlockCoord> openCells;
    private final Map<MineColumn, MineColumn> parents;
    private final MineCells cells;
    private final MineOpening entranceOpening;
    private final MineStepWalker stepWalker;
    private final MineBuildCells ore;
    private final Host host;
    private final StallWatch stall = new StallWatch();
    private Phase phase = Phase.PICK;
    private Phase aimReturn = Phase.DIG;
    private long ticks;
    private @Nullable MineColumn current;
    private boolean breaking;
    private boolean dugHere;
    private int actions;
    private int settle;
    private int dug;

    MineBuildCycle(
            SpiritEntity spirit,
            Level level,
            LevelSafetyView safety,
            BlockBreaker breaker,
            MineFinish finish,
            List<MineColumn> plan,
            Set<MineColumn> open,
            Set<MineColumn> closed,
            Set<BlockCoord> openCells,
            Map<MineColumn, MineColumn> parents,
            MineCells cells,
            MineOpening entranceOpening,
            MineStepWalker stepWalker,
            MineBuildCells ore,
            Host host) {
        this.spirit = spirit;
        this.level = level;
        this.safety = safety;
        this.breaker = breaker;
        this.finish = finish;
        this.plan = plan;
        this.open = open;
        this.closed = closed;
        this.openCells = openCells;
        this.parents = parents;
        this.cells = cells;
        this.entranceOpening = entranceOpening;
        this.stepWalker = stepWalker;
        this.ore = ore;
        this.host = host;
    }

    int dug() {
        return dug;
    }

    void aim() {
        aimReturn = phase;
        phase = Phase.AIM;
    }

    void pick() {
        phase = Phase.PICK;
    }

    void breaking() {
        breaking = true;
    }

    void tick() {
        ticks++;
        watchStall();
        if (breaking) {
            breakTick();
            return;
        }
        switch (phase) {
            case PICK -> choose();
            case WALK -> walk();
            case DIG -> dig();
            case AIM -> aimTick();
            case SETTLE -> settle();
            case TORCH -> torch();
            case EXIT -> exit();
            case DONE -> host.succeeded();
        }
    }

    private void choose() {
        Optional<MineColumn> next = MinePlan.next(plan, open, closed);
        if (next.isEmpty()) {
            List<MineColumn> unreached = plan.stream()
                    .filter(column -> !open.contains(column) && !closed.contains(column))
                    .toList();
            if (!unreached.isEmpty()) {
                fail("до " + unreached.size() + " колонок шахты не дойти пешком (первая "
                        + unreached.get(0).floor() + "): обход оставленной руды не соединил ход");
                return;
            }
            phase = Phase.TORCH;
            return;
        }
        current = next.get();
        actions = 0;
        dugHere = false;
        phase = Phase.WALK;
    }

    private void walk() {
        MineColumn column = current();
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        if (entranceOpening.waiting(
                column, feet, cells::open, cell -> ore.startBreak(cell, OptionalInt.empty()), cells::removeWalls)) {
            return;
        }
        Optional<MineColumn> parent = MinePlan.parent(column, open);
        MineStepWalker.State state =
                stepWalker.tick(column, parent.map(MineColumn::stand).orElse(null), feet);
        if (state == MineStepWalker.State.ARRIVED || state == MineStepWalker.State.VISIBLE) {
            parent.ifPresentOrElse(found -> parents.put(column, found), () -> parents.remove(column));
            phase = Phase.DIG;
        } else if (state == MineStepWalker.State.FAILED) {
            fail("к ступени " + column.stand() + " не подойти: " + stepWalker.refusal());
        }
    }

    private void dig() {
        MineColumn column = current();
        if (actions++ > MAX_ACTIONS_PER_COLUMN) {
            fail("колонка " + column.floor() + " не открывается за " + MAX_ACTIONS_PER_COLUMN
                    + " действий: сверху сыплется гравий или песок, проход не держится");
            return;
        }
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        for (BlockCoord cell : MineDigOrder.column(column, cells.wallsOf(column), feet, safety)) {
            boolean ready = cell.equals(column.floor())
                    ? ore.solidReady(cell, "пол")
                    : column.contains(cell) ? ore.cellReady(column, cell) : ore.solidReady(cell, "стенка");
            if (!ready) {
                return;
            }
        }
        settle = dugHere ? SETTLE_TICKS : 0;
        phase = Phase.SETTLE;
    }

    private void settle() {
        if (settle-- > 0) {
            return;
        }
        MineColumn column = current();
        BlockCoord floor = column.floor();
        AABB box = new AABB(
                floor.x(), floor.y() + 1, floor.z(), floor.x() + 1, floor.y() + column.height() + 2, floor.z() + 1);
        boolean falling =
                !level.getEntitiesOfClass(FallingBlockEntity.class, box).isEmpty();
        if (falling || column.cells().stream().anyMatch(cell -> !cells.open(cell))) {
            phase = Phase.DIG;
            return;
        }
        open.add(column);
        openCells.addAll(column.cells());
        phase = Phase.PICK;
    }

    private void watchStall() {
        long loop = open.size() * 1_000_003L + dug * 1_001L + ore.floors() * 7L + ore.plugs() + closed.size() * 17L;
        if (!stall.stalled(ticks, loop)) {
            return;
        }
        MineStallNote.warn(spirit.getStringUUID() + ", фаза " + phase + ", колонка "
                + (current == null ? "нет" : current.floor()) + ", дух "
                + new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()) + ", открыто "
                + open.size() + " из " + (plan.size() - closed.size()) + ", выкопано " + dug + ", полов "
                + ore.floors() + ", пробок " + ore.plugs() + ", закрыто рудой " + closed.size());
    }

    private void aimTick() {
        switch (ore.aimTick()) {
            case RUNNING -> {}
            case RESUME -> phase = aimReturn;
            case FAILED -> fail(ore.aimFailure());
        }
    }

    private void breakTick() {
        BlockBreaker.Progress progress = breaker.tick();
        if (progress == BlockBreaker.Progress.BREAKING) {
            return;
        }
        breaking = false;
        if (progress == BlockBreaker.Progress.BROKEN) {
            dug++;
            dugHere = true;
        }
    }

    private void torch() {
        switch (finish.torchTick()) {
            case RUNNING -> {}
            case DONE -> phase = Phase.EXIT;
            case FAILED -> fail(finish.failure());
        }
    }

    private void exit() {
        switch (finish.exitTick()) {
            case RUNNING -> {}
            case DONE -> phase = Phase.DONE;
            case FAILED -> fail(finish.failure());
        }
    }

    private MineColumn current() {
        MineColumn column = current;
        if (column == null) {
            throw new IllegalStateException("Колонка проходки не выбрана: фаза " + phase + " идёт после PICK");
        }
        return column;
    }

    private void fail(String reason) {
        host.failed(reason);
    }
}
