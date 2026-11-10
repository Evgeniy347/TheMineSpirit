package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseKind;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.board.lease.LeaseUndo;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.adapter.Visibility;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.lease.LeaseData;
import com.theminespirit.neoforge.lease.LeaseRun;
import com.theminespirit.neoforge.lease.LeaseStates;
import com.theminespirit.neoforge.movement.RouteLevelView;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class UndoLeaseScript implements ScriptInstance {
    private static final double REACH = 4.5;
    private static final double ARRIVAL = 0.6;

    private final SpiritEntity spirit;
    private final ServerLevel level;
    private final String leaseId;
    private final Set<BlockCoord> rolledBack = new HashSet<>();
    private final Approach approach;

    private LeaseRun run;
    private List<LeaseUndo.Step> steps;
    private int cursor;
    private BlockCoord walkingTo;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private String result;

    public UndoLeaseScript(SpiritEntity spirit, Map<String, Object> params) {
        this.spirit = spirit;
        this.level = (ServerLevel) spirit.level();
        this.approach = new Approach(spirit);
        Object id = params.get("id");
        if (!(id instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Откат аренды без id: граф не сказал, чью правку убирать");
        }
        this.leaseId = text;
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время отката аренды");
        }
        if (run == null && !attach()) {
            return outcome;
        }
        if (steps == null) {
            steps = LeaseUndo.order(lease());
        }
        if (walkingTo != null) {
            walk();
            return ScriptStatus.RUNNING;
        }
        while (cursor < steps.size()) {
            LeaseUndo.Step step = steps.get(cursor);
            if (!run.contains(pos(step.pos()))) {
                cursor++;
                continue;
            }
            BlockPos pos = pos(step.pos());
            BlockState state = level.getBlockState(pos);
            BlockCoord feet = feet();
            if (step.kind() == LeaseKind.PLACED && !state.isAir() && LeaseUndo.supportsBody(step, feet)) {
                RouteLevelView view = new RouteLevelView(spirit);
                if (!LeaseUndo.breaksNow(step, feet, view, rolledBack)) {
                    return fail("дух стоит на своей правке " + step.pos()
                            + ": не ломаю опору под собой, нужен спуск или обход");
                }
            }
            if (!inReach(pos)) {
                return walkTo(step.pos());
            }
            if (step.kind() == LeaseKind.PLACED) {
                return breakPlaced(step, pos, state);
            }
            if (step.kind() == LeaseKind.BROKEN) {
                return restoreBroken(step, pos, state);
            }
            return restoreState(step, pos);
        }
        return finish();
    }

    private boolean attach() {
        run = LeaseRun.attach(level, leaseId);
        if (run == null) {
            fail("аренда '" + leaseId + "' не найдена: откат потерял правку");
            return false;
        }
        if (run.status() == LeaseStatus.DONE) {
            outcome = ScriptStatus.SUCCEEDED;
            result = "аренда уже откатана";
            return false;
        }
        if (run.status() != LeaseStatus.ORPHANED) {
            fail("аренда '" + leaseId + "' ещё действует: откатывают только брошенную правку");
            return false;
        }
        return true;
    }

    private Lease lease() {
        return LeaseData.get(level).book().require(leaseId);
    }

    private ScriptStatus breakPlaced(LeaseUndo.Step step, BlockPos pos, BlockState state) {
        if (BreakSight.visible(spirit, pos, false) != Visibility.CLEAR) {
            return walkTo(step.pos());
        }
        BreakNotice.broken(level, pos, state, spirit, "UNDO_LEASE", PlotProtection.Ground.NONE);
        SpiritBlocks.destroy(level, pos, true, spirit, "UNDO_LEASE");
        SpiritGesture.workOn(spirit, pos);
        complete(step, pos);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus restoreBroken(LeaseUndo.Step step, BlockPos pos, BlockState state) {
        if (!state.isAir() && !state.canBeReplaced() && !BlockStates.idOf(state).equals(step.before())) {
            return fail("клетка " + step.pos() + " занята блоком " + BlockStates.idOf(state) + ": вернуть "
                    + step.before() + " некуда");
        }
        SpiritBlocks.restore(level, pos, BlockStates.stateFor(step.before()), spirit, "UNDO_LEASE");
        complete(step, pos);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus restoreState(LeaseUndo.Step step, BlockPos pos) {
        LeaseStates.apply(level, pos, step.before());
        complete(step, pos);
        return ScriptStatus.RUNNING;
    }

    private void complete(LeaseUndo.Step step, BlockPos pos) {
        cursor++;
        rolledBack.add(step.pos());
        run.rolledBack(pos);
    }

    private ScriptStatus walkTo(BlockCoord cell) {
        walkingTo = cell;
        if (!approach.isActive()) {
            Optional<BlockCoord> stand = StandSpots.of(cell, new LevelSafetyView(level), feet()).stream()
                    .findFirst();
            if (stand.isEmpty()) {
                return fail("к клетке " + cell + " нет подхода: откат ждёт пути");
            }
            approach.begin(Vec3.atBottomCenterOf(pos(stand.orElseThrow())), ARRIVAL);
        }
        return ScriptStatus.RUNNING;
    }

    private void walk() {
        switch (approach.tick()) {
            case MOVING -> {
                return;
            }
            case ARRIVED -> walkingTo = null;
            case STUCK, TIMEOUT -> fail("не подошёл к " + walkingTo + ": " + approach.refusal());
        }
    }

    private ScriptStatus finish() {
        if (!run.cells().isEmpty()) {
            return fail("после прохода осталось клеток " + run.cells().size() + ": откат не смог убрать часть правки");
        }
        outcome = ScriptStatus.SUCCEEDED;
        result = "брошенная аренда откатана: клеток " + steps.size();
        return outcome;
    }

    private boolean inReach(BlockPos pos) {
        return spirit.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
    }

    private BlockCoord feet() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        approach.cancel();
        return outcome;
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<Progress> progress() {
        int total = Math.max(1, steps == null ? 1 : steps.size());
        return Optional.of(new Progress(Math.min(cursor, total), total));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED ? Optional.of(new ScriptResult.Text(result)) : Optional.empty();
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    @Override
    public void restoreState(Map<String, Object> state) {}
}
