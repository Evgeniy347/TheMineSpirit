package com.theminespirit.neoforge.doors;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BlockPointParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class CloseDoorScript implements ScriptInstance {
    private static final double REACH = 4.5;
    private static final double ARRIVAL = 1.25;

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockCoord target;
    private final Approach approach;
    private boolean approached;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    public CloseDoorScript(SpiritEntity spirit, BlockPointParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.target = new BlockCoord(params.x(), params.y(), params.z());
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время закрытия двери");
        }
        BlockState state = level.getBlockState(toPos(target));
        if (!state.hasProperty(BlockStateProperties.OPEN)) {
            return fail("клетка " + toPos(target).toShortString() + " больше не содержит дверь: закрывать нечего");
        }
        if (!state.getValue(BlockStateProperties.OPEN)) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (!approached) {
            double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(toPos(target)));
            if (distance > REACH) {
                if (!approach.isActive()) {
                    approach.begin(new Vec3(target.x() + 0.5, target.y(), target.z() + 0.5), ARRIVAL);
                }
                switch (approach.tick()) {
                    case MOVING -> {
                        return ScriptStatus.RUNNING;
                    }
                    case ARRIVED -> approached = true;
                    case STUCK, TIMEOUT -> {
                        return fail("к двери " + toPos(target).toShortString() + " не подойти: " + approach.refusal());
                    }
                }
            } else {
                approached = true;
            }
            return ScriptStatus.RUNNING;
        }
        level.setBlock(toPos(target), state.setValue(BlockStateProperties.OPEN, false), 3);
        DoorWatchService.onClosed(level, toPos(target));
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        approach.cancel();
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(0, 1));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED ? Optional.of(new ScriptResult.Text("дверь закрыта"))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of("approached", approached);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        approached = (boolean) state.getOrDefault("approached", false);
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
