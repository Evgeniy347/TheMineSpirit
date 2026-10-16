package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.LightCell;
import com.theminespirit.core.building.LightPlan;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.script.LightAreaParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class LightAreaSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        PLAN,
        PICK,
        APPROACH,
        PLACE
    }

    private static final String TORCH_BLOCK_ID = "minecraft:torch";

    private final SpiritEntity spirit;
    private final Level level;
    private final LightAreaParams params;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;

    private State state = State.PLAN;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private List<BlockCoord> torches = List.of();
    private int cursor;
    private int placed;
    private int skipped;
    private int missing;
    private BlockCoord currentTarget;
    private final BuildResult tally = new BuildResult();

    public LightAreaSession(SpiritEntity spirit, LightAreaParams params) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кто освещает");
        this.level = spirit.level();
        this.params = Objects.requireNonNull(params, "Параметры не могут быть null: без них неизвестна область");
        this.safety = new LevelSafetyView(level);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время освещения территории");
        }
        switch (state) {
            case PLAN -> plan();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case PLACE -> placeTick();
        }
        return outcome;
    }

    public void cancel() {
        approach.cancel();
    }

    public void suspend() {
        approach.suspend();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(cursor, Math.max(torches.size(), 1));
    }

    public Map<String, Object> resultData() {
        return tally.toData();
    }

    public Map<String, Object> saveState() {
        return new BuildState(placed, 0, skipped, 0, outcome == Outcome.RUNNING).toMap();
    }

    public void restoreState(Map<String, Object> saved) {
        BuildState snapshot = BuildState.fromMap(saved);
        for (int i = 0; i < snapshot.placed(); i++) {
            tally.placed(TORCH_BLOCK_ID);
            placed++;
        }
        for (int i = 0; i < snapshot.skipped(); i++) {
            tally.skipped();
            skipped++;
        }
        state = State.PLAN;
        cursor = 0;
        torches = List.of();
    }

    private void plan() {
        torches = LightPlan.plan(params.x(), params.z(), params.radius(),
                scanCells(params.x(), params.y(), params.z(), params.radius())).torches();
        state = State.PICK;
    }

    private void pick() {
        if (cursor >= torches.size()) {
            finish();
            return;
        }
        currentTarget = torches.get(cursor);
        BlockPos pos = toPos(currentTarget);
        if (level.getBlockState(pos).is(Blocks.TORCH)) {
            placed++;
            tally.placed(TORCH_BLOCK_ID);
            cursor++;
            return;
        }
        if (spirit.getInventory().countItem(Items.TORCH) < 1) {
            missing = torches.size() - cursor;
            finish();
            return;
        }
        double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
        if (distance <= BlockBreaker.REACH) {
            state = State.PLACE;
            return;
        }
        List<BlockCoord> stands = StandSpots.of(currentTarget, safety, spiritCoord());
        if (stands.isEmpty()) {
            skipCurrent();
            return;
        }
        approach.begin(new Vec3(stands.get(0).x() + 0.5, stands.get(0).y(), stands.get(0).z() + 0.5),
                BuildTuning.STAND_ARRIVAL);
        state = State.APPROACH;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> state = State.PLACE;
            case STUCK, TIMEOUT -> skipCurrent();
        }
    }

    private void placeTick() {
        BlockPlacer.Begin begin = placer.begin(toPos(currentTarget), Blocks.TORCH.defaultBlockState(), TORCH_BLOCK_ID);
        if (begin instanceof BlockPlacer.Started) {
            placed++;
            tally.placed(TORCH_BLOCK_ID);
            cursor++;
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockPlacer.Protected) {
            skipCurrent();
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        if (refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL) {
            missing = torches.size() - cursor;
            finish();
            return;
        }
        skipCurrent();
    }

    private void skipCurrent() {
        skipped++;
        tally.skipped();
        cursor++;
        state = State.PICK;
    }

    private void finish() {
        tally.supplies(spirit.getInventory().countItem(Items.TORCH), missing);
        outcome = Outcome.SUCCEEDED;
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        tally.failed();
        cancel();
        return outcome;
    }

    private List<LightCell> scanCells(int centerX, int centerY, int centerZ, int radius) {
        List<LightCell> cells = new ArrayList<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                    continue;
                }
                BlockPos pos = surface(x, z);
                if (pos == null || Math.abs(pos.getY() - centerY) > radius) {
                    continue;
                }
                cells.add(new LightCell(new BlockCoord(x, pos.getY(), z),
                        level.getBrightness(LightLayer.BLOCK, pos), isPlaceable(pos)));
            }
        }
        return cells;
    }

    private BlockPos surface(int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return null;
        }
        return pos;
    }

    private boolean isPlaceable(BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        if (!at.isAir() && !at.canBeReplaced()) {
            return false;
        }
        if (!at.getFluidState().isEmpty()) {
            return false;
        }
        if (level.getBlockState(pos.below()).is(BlockTags.LEAVES)) {
            return false;
        }
        if (!hasSupport(pos)) {
            return false;
        }
        return !(spirit.protectionGate().checkPlace(pos) instanceof BlockProtection.Refused);
    }

    private boolean hasSupport(BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(side);
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, side.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
