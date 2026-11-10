package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.MineTunnel;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class TunnelReach {
    enum State {
        RUNNING,
        ARRIVED,
        FAILED
    }

    private enum Phase {
        NEXT,
        DIG,
        BREAK,
        APPROACH,
        RETRACT_WALK,
        RETRACT_FILL
    }

    private record Dug(BlockCoord cell, String before) {}

    private static final double ARRIVAL = 0.6;
    private static final int STEP_LIMIT_TICKS = 400;

    private final SpiritEntity spirit;
    private final ServerLevel level;
    private final LevelSafetyView safety;
    private final BlockCoord from;
    private final List<MineTunnel.Step> steps;
    private final LeaseRun lease;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final List<List<Dug>> dug = new ArrayList<>();

    private Phase phase = Phase.NEXT;
    private int index;
    private int pending;
    private int stepTicks;
    private int retract;
    private BlockCoord retractTo;
    private boolean arrived;
    private String failureReason;
    private State outcome = State.RUNNING;

    private TunnelReach(
            SpiritEntity spirit, LevelSafetyView safety, BlockCoord from, List<MineTunnel.Step> steps, LeaseRun lease) {
        this.spirit = spirit;
        this.level = (ServerLevel) spirit.level();
        this.safety = safety;
        this.from = from;
        this.steps = List.copyOf(steps);
        this.lease = lease;
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
    }

    static @Nullable TunnelReach begin(
            SpiritEntity spirit, BlockCoord target, BlockCoord feet, LevelSafetyView safety, int maxSteps) {
        MineTunnel.Plan plan = MineTunnel.plan(
                target, feet, safety, maxSteps, cell -> !cell.equals(target) && protectedCell(spirit, cell));
        if (!plan.found()) {
            return null;
        }
        LeaseRun lease = LeaseRun.open(spirit, "undo_approach");
        return new TunnelReach(spirit, safety, feet, plan.steps(), lease);
    }

    State tick() {
        if (outcome != State.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время подхода к клетке отката");
        }
        if (index >= steps.size()) {
            arrived = true;
            outcome = State.ARRIVED;
            return outcome;
        }
        stepTicks++;
        if (stepTicks > STEP_LIMIT_TICKS) {
            return fail("шаг прохода " + steps.get(index).stand() + " не пройден за " + STEP_LIMIT_TICKS + " тиков");
        }
        switch (phase) {
            case NEXT -> beginStep();
            case DIG -> digTick();
            case BREAK -> breakTick();
            case APPROACH -> walkTick();
            default -> fail("подход копкой ждал шага прохода, а пошёл в откат");
        }
        return outcome;
    }

    State retract() {
        if (outcome == State.FAILED) {
            return outcome;
        }
        if (!arrived) {
            return fail("проход к клетке отката не дойден: разбирать нечего");
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время разбора прохода к клетке отката");
        }
        if (outcome == State.ARRIVED) {
            retract = steps.size() - 1;
            outcome = State.RUNNING;
            phase = Phase.RETRACT_FILL;
        }
        stepTicks++;
        if (stepTicks > STEP_LIMIT_TICKS) {
            return fail("разбор прохода не уложился в " + STEP_LIMIT_TICKS + " тиков на шаге " + retract);
        }
        switch (phase) {
            case RETRACT_FILL -> {
                if (retract < 0) {
                    if (lease.cells().isEmpty()) {
                        lease.commit();
                    } else {
                        lease.orphan();
                    }
                    outcome = State.ARRIVED;
                    return outcome;
                }
                retractTo = retract == 0 ? from : steps.get(retract - 1).stand();
                beginWalk(retractTo);
                phase = Phase.RETRACT_WALK;
            }
            case RETRACT_WALK -> walkRetract();
            default -> fail("разбор прохода ждал закладки клеток, а пошёл вперёд");
        }
        return outcome;
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
        lease.orphan();
    }

    String failureReason() {
        return failureReason == null ? "" : failureReason;
    }

    private void beginStep() {
        MineTunnel.Step step = steps.get(index);
        List<BlockCoord> cells = new ArrayList<>(step.dig());
        cells.sort(Comparator.comparingInt(BlockCoord::y).reversed());
        List<Dug> recorded = new ArrayList<>(cells.size());
        dug.add(recorded);
        pending = 0;
        stepTicks = 0;
        phase = Phase.DIG;
    }

    private void digTick() {
        MineTunnel.Step step = steps.get(index);
        List<Dug> recorded = dug.get(index);
        while (pending < step.dig().size()) {
            BlockCoord cell = ordered(index).get(pending);
            BlockPos pos = pos(cell);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
                pending++;
                continue;
            }
            if (!(MiningSafety.canBreak(cell, safety) instanceof BreakSafety.Safe)) {
                fail("ломать " + cell + " на подходе к откату небезопасно: рядом жидкость или опасный блок");
                return;
            }
            BlockBreaker.Begin begin = breaker.begin(pos);
            if (begin instanceof BlockBreaker.Started) {
                String before = BlockStates.idOf(state);
                recorded.add(new Dug(cell, before));
                lease.broken(pos, before);
                phase = Phase.BREAK;
                return;
            }
            if (begin instanceof BlockBreaker.Protected refused) {
                fail("ломать " + cell + " на подходе к откату запрещено: " + refused.reason());
                return;
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            fail("не пробить " + cell + " на подходе к откату: " + refused.message());
            return;
        }
        beginWalk(step.stand());
        phase = Phase.APPROACH;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {}
            case BROKEN, GONE -> phase = Phase.DIG;
        }
    }

    private void walkTick() {
        MineTunnel.Step step = steps.get(index);
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                index++;
                phase = Phase.NEXT;
            }
            case STUCK, TIMEOUT ->
                fail("к стоянке " + step.stand() + " на подходе к откату не подойти: "
                        + (approach.refusal() == null ? "путь не найден" : approach.refusal()));
        }
    }

    private void walkRetract() {
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                phase = Phase.RETRACT_FILL;
                fill(retract);
                approach.cancel();
            }
            case STUCK, TIMEOUT ->
                fail("не вернуться к " + retractTo + " при разборе прохода: "
                        + (approach.refusal() == null ? "путь не найден" : approach.refusal()));
        }
    }

    private void beginWalk(BlockCoord stand) {
        stepTicks = 0;
        approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), ARRIVAL);
    }

    private void fill(int step) {
        List<Dug> recorded = dug.get(step);
        for (int at = recorded.size() - 1; at >= 0; at--) {
            Dug cell = recorded.get(at);
            BlockPos pos = pos(cell.cell());
            if (!level.getBlockState(pos).isAir()) {
                lease.forget(pos);
                continue;
            }
            SpiritBlocks.restore(level, pos, BlockStates.stateFor(cell.before()), spirit, "UNDO_LEASE");
            lease.forget(pos);
        }
        retract--;
    }

    private List<BlockCoord> ordered(int step) {
        List<BlockCoord> cells = new ArrayList<>(steps.get(step).dig());
        cells.sort(Comparator.comparingInt(BlockCoord::y).reversed());
        return cells;
    }

    private State fail(String reason) {
        failureReason = reason;
        outcome = State.FAILED;
        breaker.cancel();
        approach.cancel();
        return outcome;
    }

    private static boolean protectedCell(SpiritEntity spirit, BlockCoord cell) {
        return spirit.level() instanceof ServerLevel server
                && SettlementPlotStore.liveSite(server)
                        .map(site -> PlotProtection.check(
                                        site,
                                        cell,
                                        PlotProtection.Action.DIG,
                                        PlotProtection.Ground.NONE,
                                        PlanBuilding.NONE)
                                instanceof PlotProtection.Refused)
                        .orElse(false);
    }

    private static BlockPos pos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
