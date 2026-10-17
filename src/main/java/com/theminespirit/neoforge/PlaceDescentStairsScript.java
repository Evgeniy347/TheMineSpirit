package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.DescentLevels;
import com.theminespirit.core.mining.DescentPlan;
import com.theminespirit.core.mining.DescentStairStep;
import com.theminespirit.core.mining.DescentStairsPlan;
import com.theminespirit.core.mining.DescentStop;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.DigDescentParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class PlaceDescentStairsScript implements ScriptInstance {
    private static final String STAIR_BLOCK_ID = "minecraft:stone_stairs";

    private final FillSession session;
    private final DescentStairsPlan plan;

    PlaceDescentStairsScript(SpiritEntity spirit, DigDescentParams params) {
        this(setup(spirit, params.direction(), params.y(), DescentLevels.loadDefault()));
    }

    private PlaceDescentStairsScript(Setup setup) {
        this.plan = setup.plan();
        this.session = new FillSession(setup.spirit(), STAIR_BLOCK_ID, setup.cells());
    }

    @Override
    public ScriptStatus tick() {
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> ScriptStatus.FAILED;
        };
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(session.progress());
    }

    @Override
    public Optional<ScriptResult> result() {
        if (session.outcome() != FillSession.Outcome.SUCCEEDED) {
            return Optional.empty();
        }
        int entryFloor = plan.entry().y() - 1;
        int y = plan.steps().isEmpty() ? entryFloor : plan.steps().get(plan.steps().size() - 1).floor();
        return Optional.of(new ScriptResult.Data(Map.of(
                "reached", plan.stop() == DescentStop.TARGET_Y,
                "y", y,
                "depth", entryFloor - y,
                "blocks_placed", session.placed(),
                "reason", plan.dangerReason())));
    }

    @Override
    public void cancel() {
        session.cancel();
    }

    @Override
    public void suspend() {
        session.suspend();
    }

    @Override
    public String failureReason() {
        return session.failureReason();
    }

    @Override
    public Map<String, Object> saveState() {
        return session.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        session.restoreState(state);
    }

    private record Setup(SpiritEntity spirit, DescentStairsPlan plan, List<BlockCoord> cells) {
    }

    private static Setup setup(SpiritEntity spirit, DescentDirection direction, int targetY, DescentLevels levels) {
        Level level = spirit.level();
        LevelSafetyView safety = new LevelSafetyView(level);
        BlockPos feet = spirit.blockPosition();
        BlockCoord entry = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        DescentPlan level1 = DescentPlan.from(safety, entry, direction, targetY,
                DescentData.loadDefault().torchInterval());
        DescentStairsPlan plan = DescentStairsPlan.from(safety, level1, levels.widenWidth(), levels.stairWidth());
        List<BlockCoord> cells = new ArrayList<>();
        for (DescentStairStep step : plan.steps()) {
            cells.addAll(step.stairCells());
        }
        return new Setup(spirit, plan, List.copyOf(cells));
    }
}
