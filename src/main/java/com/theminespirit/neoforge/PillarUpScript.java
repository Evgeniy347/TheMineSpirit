package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.PillarUpParams;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PillarUpScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final Level level;
    private final int height;
    private final String requestedBlock;
    private final BlockPlacer placer;
    private final Approach approach;

    private int baseY;
    private int built;
    private boolean initialized;
    private String failureReason;
    private int placed;
    private ScriptStatus outcome = ScriptStatus.RUNNING;

    public PillarUpScript(SpiritEntity spirit, PillarUpParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.height = params.height();
        this.requestedBlock = params.block();
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки столба");
        }
        if (!initialized) {
            baseY = spirit.blockPosition().getY();
            initialized = true;
        }
        if (built >= height) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        String blockId = resolveBlock();
        if (blockId == null) {
            return fail("нет материала для столба: нужны земля или булыжник, есть 0");
        }
        BlockPos target = new BlockPos(spirit.blockPosition().getX(), baseY + built, spirit.blockPosition().getZ());
        BlockState wanted = BlockStates.stateFor(blockId);
        // danger checks
        if (!level.getBlockState(target).isAir()) {
            return fail("клетка " + target.toShortString() + " занята: потолок или помеха, столб не строится");
        }
        if (!level.getFluidState(target).isEmpty()) {
            return fail("в клетке " + target.toShortString() + " жидкость: лава или вода, столб не строится");
        }
        // adjacent fluid check
        for (Direction dir : Direction.values()) {
            if (!level.getFluidState(target.relative(dir)).isEmpty()) {
                return fail("рядом с клеткой " + target.toShortString() + " жидкость: лава или вода, столб не строится");
            }
        }
        if (target.getY() >= CatalogLimits.WORLD_Y_MAX) {
            return fail("достигнут потолок мира, столб не строится дальше");
        }
        // support check via placer handles; but if no support and not first? place will refuse
        double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(target));
        if (distance > BlockBreaker.REACH) {
            // shouldn't happen for self pillar
        }
        if (spirit.getY() < target.getY() + 0.5 && !spirit.onGround()) {
            // wait to land
        }
        if (spirit.onGround() && spirit.getY() < target.getY() + 0.5 && built > 0) {
            spirit.getJumpControl().jump();
            return ScriptStatus.RUNNING;
        }
        BlockPlacer.Begin begin = placer.begin(target, wanted, blockId);
        if (begin instanceof BlockPlacer.Started) {
            built++;
            placed++;
            return ScriptStatus.RUNNING;
        }
        if (begin instanceof BlockPlacer.Protected p) {
            return fail("ставить запрещено: " + p.reason());
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        return switch (refused.reason()) {
            case NO_MATERIAL -> fail("нет материала '" + blockId + "': нужно 1, есть 0");
            case OCCUPIED -> fail("клетка " + target.toShortString() + " занята: потолок или помеха");
            case NO_SUPPORT -> fail("под клеткой " + target.toShortString() + " нет опоры");
            case OUT_OF_REACH -> fail("клетка " + target.toShortString() + " вне досягаемости");
            case CHUNK_NOT_LOADED -> fail("чанк с клеткой " + target.toShortString() + " не загружен");
        };
    }

    private String resolveBlock() {
        if (requestedBlock != null && !requestedBlock.isBlank()) {
            return requestedBlock;
        }
        // cheap from inventory: dirt or cobblestone
        var dirt = Items.DIRT;
        var cobble = Items.COBBLESTONE;
        if (spirit.getInventory().countItem(dirt) > 0) return "minecraft:dirt";
        if (spirit.getInventory().countItem(cobble) > 0) return "minecraft:cobblestone";
        // any dirt-like?
        return null;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        approach.cancel();
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(placed, height));
    }

    @Override
    public Optional<ScriptResult> result() {
        if (outcome == ScriptStatus.SUCCEEDED) {
            return Optional.of(new ScriptResult.Text("поднято на " + built));
        }
        return Optional.empty();
    }

    @Override
    public void cancel() { approach.cancel(); }

    @Override
    public void suspend() { approach.suspend(); }

    @Override
    public String failureReason() { return failureReason; }

    @Override
    public Map<String, Object> saveState() { return Map.of(); }

    @Override
    public void restoreState(Map<String, Object> state) { }
}
