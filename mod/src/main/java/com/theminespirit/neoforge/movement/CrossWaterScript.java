package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CrossWaterParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.SwimParams;
import com.theminespirit.neoforge.GotoScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

public final class CrossWaterScript implements ScriptInstance {
    private static final int SWIM_DEPTH = 2;

    private final SpiritEntity spirit;
    private final CrossWaterParams params;
    private final BlockCoord target;
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
        int width = waterWidth(new BlockCoord(feet.getX(), floorY, feet.getZ()), target);
        if (width == 0 || deepest < SWIM_DEPTH) {
            delegate = new GotoScript(spirit, new GotoParams(params.x(), params.y(), params.z()));
            return;
        }
        delegate = new SwimScript(spirit, new SwimParams(params.x(), params.y(), params.z()));
    }

    private int waterWidth(BlockCoord from, BlockCoord to) {
        int x0 = from.x();
        int z0 = from.z();
        int x1 = to.x();
        int z1 = to.z();
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0);
        int sz = Integer.compare(z1, z0);
        int err = dx - dz;
        int x = x0;
        int z = z0;
        int run = 0;
        int widest = 0;
        while (true) {
            if (isWater(x, from.y(), z)) {
                run++;
                widest = Math.max(widest, run);
                deepest = Math.max(deepest, depthAt(x, from.y(), z));
            } else {
                run = 0;
            }
            if (x == x1 && z == z1) {
                break;
            }
            int doubled = 2 * err;
            if (doubled > -dz) {
                err -= dz;
                x += sx;
            }
            if (doubled < dx) {
                err += dx;
                z += sz;
            }
        }
        return widest;
    }

    private int depthAt(int x, int top, int z) {
        int depth = 0;
        while (depth < SWIM_DEPTH && isWater(x, top - depth, z)) {
            depth++;
        }
        return depth;
    }

    private boolean isWater(int x, int y, int z) {
        return spirit.level().getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
    }
}
