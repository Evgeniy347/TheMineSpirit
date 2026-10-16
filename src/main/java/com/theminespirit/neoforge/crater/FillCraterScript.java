package com.theminespirit.neoforge.crater;

import com.theminespirit.core.building.BuildMaterials;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.Crater;
import com.theminespirit.core.building.CraterPlan;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.BuildReservationHub;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import com.theminespirit.neoforge.inventory.SpiritBuildSupplies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Засыпка воронки от взрыва: берёт землю из инвентаря и ближних сундуков (через существующее
 * снабжение acquire/withdraw) и ставит по плану {@link CraterPlan} снизу вверх.
 *
 * <p>Нехватка земли не роняет задачу: засыпается сколько хватило, итог называет засыпанное и нехватку.
 */
public final class FillCraterScript implements ScriptInstance {
    static final String FILL_BLOCK = "minecraft:dirt";

    private final SpiritEntity spirit;
    private final Level level;
    private final Crater crater;
    private final CraterPlan.Plan plan;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;
    private final BuildResult tally = new BuildResult();

    private TaskRunner withdrawRunner;
    private boolean withdrawPlanned;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private int cursor;
    private int placed;
    private int skipped;

    public FillCraterScript(SpiritEntity spirit, Crater crater) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.crater = crater;
        this.plan = CraterPlan.plan(new LevelCraterSurface(level, spirit, crater));
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
    }

    Crater crater() {
        return crater;
    }

    CraterPlan.Plan plan() {
        return plan;
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время засыпки воронки");
        }
        if (!withdrawPlanned) {
            withdrawPlanned = true;
            withdrawRunner = startWithdraw();
        }
        if (withdrawRunner != null) {
            ScriptStatus withdraw = tickWithdraw();
            if (withdraw == ScriptStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            withdrawRunner = null;
        }
        return placeTick();
    }

    private TaskRunner startWithdraw() {
        BuildMaterials.Plan supplies = BuildMaterials.plan(FILL_BLOCK, Math.max(plan.blockCount(), 1),
                new SpiritBuildSupplies(spirit));
        if (supplies.steps().isEmpty()) {
            return null;
        }
        List<TaskNode> steps = new ArrayList<>(supplies.steps());
        TaskNode node = new SequenceNode(steps);
        return TaskRunner.start(node, SpiritScripts.registryFor(spirit), ConditionRegistry.of(Map.of()));
    }

    private ScriptStatus tickWithdraw() {
        return switch (withdrawRunner.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED, CANCELLED -> ScriptStatus.SUCCEEDED;
        };
    }

    private ScriptStatus placeTick() {
        while (cursor < plan.cells().size()) {
            BlockCoord cell = plan.cells().get(cursor);
            BlockPos pos = toPos(cell);
            BlockState current = level.getBlockState(pos);
            if (current.is(BlockStates.blockFor(FILL_BLOCK))) {
                cursor++;
                continue;
            }
            if (reservations.foreign(cell, spirit.getUUID())) {
                skip(cell);
                continue;
            }
            reservations.reserve(cell, spirit.getUUID());
            List<BlockCoord> stands = StandSpots.of(cell, new LevelSafetyView(level), spiritCoord());
            if (stands.isEmpty()) {
                skip(cell);
                continue;
            }
            BlockCoord stand = stands.get(0);
            if (!approach.isActive()) {
                approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
            }
            return switch (approach.tick()) {
                case MOVING -> ScriptStatus.RUNNING;
                case ARRIVED -> place(cell);
                case STUCK, TIMEOUT -> {
                    skip(cell);
                    yield ScriptStatus.RUNNING;
                }
            };
        }
        return finish();
    }

    private ScriptStatus place(BlockCoord cell) {
        BlockPos pos = toPos(cell);
        BlockPlacer.Begin begin = placer.begin(pos, BlockStates.stateFor(FILL_BLOCK), FILL_BLOCK);
        release(cell);
        if (begin instanceof BlockPlacer.Started) {
            placed++;
            tally.placed(FILL_BLOCK);
            cursor++;
            return ScriptStatus.RUNNING;
        }
        skip(cell);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus finish() {
        releaseAll();
        int have = placed + countInInventory();
        tally.supplies(have, Math.max(plan.blockCount() - placed - skipped, 0));
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    private int countInInventory() {
        return BlockItems.countIn(spirit.getInventory(), FILL_BLOCK);
    }

    private void skip(BlockCoord cell) {
        skipped++;
        tally.skipped();
        release(cell);
        cursor++;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        cancel();
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(Math.min(cursor, plan.cells().size()), Math.max(plan.cells().size(), 1)));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED ? Optional.of(new ScriptResult.Data(tally.toData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        approach.cancel();
        if (withdrawRunner != null) {
            withdrawRunner.cancel();
        }
        releaseAll();
    }

    @Override
    public void suspend() {
        approach.suspend();
        if (withdrawRunner != null) {
            withdrawRunner.suspend();
        }
    }

    @Override
    public void resume() {
        if (withdrawRunner != null) {
            withdrawRunner.resume();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> state = new HashMap<>(new BuildState(placed, 0, skipped, 0, outcome == ScriptStatus.RUNNING).toMap());
        state.put("cursor", cursor);
        return Map.copyOf(state);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        BuildState snapshot = BuildState.fromMap(state);
        placed = Math.max(placed, snapshot.placed());
        skipped = Math.max(skipped, snapshot.skipped());
        for (int i = 0; i < snapshot.placed(); i++) {
            tally.placed(FILL_BLOCK);
        }
        for (int i = 0; i < snapshot.skipped(); i++) {
            tally.skipped();
        }
        if (state.get("cursor") instanceof Number number) {
            cursor = Math.max(cursor, Math.min(number.intValue(), plan.cells().size()));
        }
        withdrawPlanned = true;
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private void release(BlockCoord cell) {
        reservations.release(cell, spirit.getUUID());
    }

    private void releaseAll() {
        reservations.releaseAll(spirit.getUUID());
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}