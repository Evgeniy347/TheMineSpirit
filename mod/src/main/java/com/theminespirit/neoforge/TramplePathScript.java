package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.PathGround;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.town.PlotProtection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class TramplePathScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final int count;
    private final BlockLocator.Search search;
    private final BlockPlacer placer;
    private final Approach approach;
    private final Set<BlockPos> tried = new HashSet<>();
    private int trampled;
    private boolean exhausted;
    private String failureReason;

    public TramplePathScript(SpiritEntity spirit, int count, int radius) {
        this.spirit = spirit;
        this.count = count;
        this.search = BlockLocator.search(
                spirit.level(),
                spirit.blockPosition(),
                radius,
                Set.of(
                        Blocks.DIRT,
                        Blocks.GRASS_BLOCK,
                        Blocks.PODZOL,
                        Blocks.COARSE_DIRT,
                        Blocks.ROOTED_DIRT,
                        Blocks.MYCELIUM));
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.PLAN_BUILD);
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время топтания дорожки");
        }
        if (trampled >= count) {
            return ScriptStatus.SUCCEEDED;
        }
        if (approach.isActive()) {
            switch (approach.tick()) {
                case MOVING -> {
                    return ScriptStatus.RUNNING;
                }
                case ARRIVED -> {}
                case STUCK, TIMEOUT -> approach.cancel();
            }
        }
        if (exhausted) {
            return fail("негде топтать дорожку: подходящей земли или травы в радиусе не осталось");
        }
        List<BlockPos> found = search.step(ScanTuning.SLICES_PER_TICK);
        BlockPos next = null;
        for (BlockPos pos : found) {
            if (!tried.add(pos)) {
                continue;
            }
            if (withinReach(pos)) {
                if (trample(pos)) {
                    return ScriptStatus.RUNNING;
                }
                continue;
            }
            if (next == null) {
                next = pos;
            }
        }
        if (next != null) {
            approach.begin(Vec3.atCenterOf(next), ChopTuning.STAND_DISTANCE);
            return ScriptStatus.RUNNING;
        }
        if (search.done()) {
            exhausted = true;
        }
        return ScriptStatus.RUNNING;
    }

    private boolean trample(BlockPos pos) {
        if (!PathGround.openAbove(BlockStates.idOf(spirit.level().getBlockState(pos.above())))) {
            return false;
        }
        BlockPlacer.Begin begin = placer.begin(pos, Blocks.DIRT_PATH.defaultBlockState(), "minecraft:dirt_path");
        if (begin instanceof BlockPlacer.Started) {
            trampled++;
            LOGGER.info("Дух {} утоптал дорожку в {}: {}", spirit.getUUID(), toCoord(pos), trampled);
            return true;
        }
        return false;
    }

    private boolean withinReach(BlockPos pos) {
        return spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= BlockBreaker.REACH;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(Math.min(trampled, count), count));
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Text("утоптано дорожек " + trampled + " из " + count));
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
