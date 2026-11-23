package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CrossWaterParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.SwimParams;
import com.theminespirit.core.script.api.CrossingSpan;
import com.theminespirit.neoforge.GotoScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class CrossWaterScript implements ScriptInstance {
    private static final int SURFACE_SCAN = 6;

    private final SpiritEntity spirit;
    private final CrossWaterParams params;
    private final BlockCoord target;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private ScriptInstance delegate;
    private boolean planned;
    private int deepest;
    private String failureReason;

    public CrossWaterScript(SpiritEntity spirit, CrossWaterParams params) {
        this.spirit = spirit;
        this.params = params;
        this.target = new BlockCoord(params.x(), params.y(), params.z());
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез во время переправы";
            return ScriptStatus.FAILED;
        }
        if (!planned) {
            planned = true;
            plan();
        }
        return delegate.tick();
    }

    @Override
    public void cancel() {
        if (delegate != null) {
            delegate.cancel();
        }
    }

    @Override
    public void suspend() {
        if (delegate != null) {
            delegate.suspend();
        }
    }

    @Override
    public String failureReason() {
        if (delegate != null && delegate.failureReason() != null) {
            return delegate.failureReason();
        }
        return failureReason;
    }

    @Override
    public Optional<Progress> progress() {
        return delegate == null ? Optional.empty() : delegate.progress();
    }

    @Override
    public Optional<ScriptResult> result() {
        return delegate == null ? Optional.empty() : delegate.result();
    }

    private void plan() {
        BlockPos feet = spirit.blockPosition();
        int floorY = feet.getY() - 1;
        CrossingSpan span = CrossingSpan.ofSurface(
                new BlockCoord(feet.getX(), floorY, feet.getZ()),
                target,
                this::waterSurfaceAt,
                cell -> depthAt(cell.x(), cell.y(), cell.z()));
        deepest = span.deepest();
        if (!span.crosses() || deepest < MovementConfig.swimDepth()) {
            delegate = new GotoScript(spirit, new GotoParams(params.x(), params.y(), params.z()));
            return;
        }
        BlockCoord shore = span.dry() == null ? null : standAt(span.dry());
        if (shore != null) {
            span = new CrossingSpan(span.width(), span.deepest(), shore, span.wet());
        }
        delegate = new SwimScript(spirit, new SwimParams(params.x(), params.y(), params.z()), span);
    }

    private @Nullable BlockCoord waterSurfaceAt(BlockCoord cell) {
        for (int y = cell.y(); y > cell.y() - SURFACE_SCAN; y--) {
            if (y < spirit.level().getMinBuildHeight()) {
                return null;
            }
            cursor.set(cell.x(), y, cell.z());
            if (!spirit.level().isLoaded(cursor)) {
                return null;
            }
            BlockState state = spirit.level().getBlockState(cursor);
            if (state.getFluidState().is(FluidTags.WATER)) {
                return new BlockCoord(cell.x(), y, cell.z());
            }
            if (!state.getCollisionShape(spirit.level(), cursor).isEmpty()) {
                return null;
            }
        }
        return null;
    }

    private @Nullable BlockCoord standAt(BlockCoord cell) {
        for (int y = cell.y(); y > cell.y() - SURFACE_SCAN; y--) {
            if (y < spirit.level().getMinBuildHeight()) {
                return null;
            }
            cursor.set(cell.x(), y, cell.z());
            if (!spirit.level().isLoaded(cursor)) {
                return null;
            }
            BlockState state = spirit.level().getBlockState(cursor);
            if (!state.getCollisionShape(spirit.level(), cursor).isEmpty()) {
                return new BlockCoord(cell.x(), y + 1, cell.z());
            }
        }
        return null;
    }

    private int depthAt(int x, int top, int z) {
        int depth = 0;
        while (depth < MovementConfig.swimDepth() && isWater(x, top - depth, z)) {
            depth++;
        }
        return depth;
    }

    private boolean isWater(int x, int y, int z) {
        return spirit.level().getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
    }
}
