package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class FillSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STAND_LOGGED = 4;
    private static final int LONG_STATE_TICKS = 200;

    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        EXIT,
        PICK,
        APPROACH,
        PILLAR,
        DONE
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final String blockId;
    private final BlockState wanted;
    private final Set<BlockCoord> region;
    private int stateTicks;
    private @Nullable State lastState;
    private @Nullable BlockCoord currentStand;
    private final List<BlockCoord> order = new ArrayList<>();
    private final LevelSafetyView safety;
    private final FillStands standView;
    private final FillTerrain terrain;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;

    private State state = State.PICK;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private int cursor;
    private int brokenY = Integer.MAX_VALUE;
    private int passPlaced;
    private BlockCoord current;
    private final FillTally tally = new FillTally();
    private @Nullable PillarColumn pillar;

    public FillSession(SpiritEntity spirit, String blockId, BuildBox box) {
        this(spirit, blockId, box.cellsBottomUp());
    }

    public FillSession(SpiritEntity spirit, String blockId, List<BlockCoord> cells) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.blockId = blockId;
        this.wanted = BlockStates.stateFor(blockId);
        this.region = Set.copyOf(cells);
        this.safety = new LevelSafetyView(level);
        this.standView = new FillStands(spirit, level, blockId, region, safety);
        this.terrain = new FillTerrain(level, spirit, wanted, region);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        replan();
        if (standView.insideBox(spirit.blockPosition())) {
            state = State.EXIT;
        }
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки");
        }
        stateTicks = state == lastState ? stateTicks + 1 : 0;
        lastState = state;
        if (stateTicks > 0 && stateTicks % LONG_STATE_TICKS == 0) {
            LOGGER.info(
                    "[fill] {}: {} тиков в {} у {} (стоянка {}, дух в {}, в воде {})",
                    spirit.getName().getString(),
                    stateTicks,
                    state,
                    current,
                    currentStand,
                    spirit.blockPosition().toShortString(),
                    spirit.isInWater());
        }
        switch (state) {
            case EXIT -> exitTick();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case PILLAR -> pillarTick();
            case DONE -> finish();
        }
        return outcome;
    }

    public void cancel() {
        approach.cancel();
        releaseAll();
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
        int total = order.size();
        return new ScriptInstance.Progress(Math.min(cursor, total), Math.max(total, 1));
    }

    public int placed() {
        return tally.placedCount();
    }

    public int skipped() {
        return tally.skippedCount();
    }

    public String blockId() {
        return blockId;
    }

    public Map<String, Object> resultData() {
        return tally.data();
    }

    public int blockReads() {
        return terrain.blockReads();
    }

    public Map<String, Object> saveState() {
        return tally.save(outcome == Outcome.RUNNING);
    }

    public void restoreState(Map<String, Object> saved) {
        tally.restore(saved, blockId);
    }

    private void exitTick() {
        BlockCoord exit = standView.exitStand();
        if (exit == null) {
            state = State.PICK;
            return;
        }
        if (!approach.isActive()) {
            approach.begin(new Vec3(exit.x() + 0.5, exit.y(), exit.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> state = State.PICK;
            case STUCK, TIMEOUT -> state = State.PICK;
        }
    }

    private void pick() {
        while (cursor < order.size()) {
            BlockCoord cell = order.get(cursor);
            if (cell.y() > brokenY) {
                endPass();
                return;
            }
            BlockPos pos = toPos(cell);
            terrain.countRead();
            BlockState current = level.getBlockState(pos);
            if (current.is(wanted.getBlock())) {
                tally.prebuiltCount();
                cursor++;
                continue;
            }
            if (reservations.foreign(cell, spirit.getUUID())) {
                tally.foreignSkipped();
                tally.skipped();
                brokenY = Math.min(brokenY, cell.y());
                cursor++;
                continue;
            }
            reservations.reserve(cell, spirit.getUUID());
            this.current = cell;
            if (cell.equals(spiritCoord())) {
                pillar = standView.pillarHere(cell);
                state = State.PILLAR;
                return;
            }
            if (standView.inReach(cell)) {
                placeCurrent();
                return;
            }
            List<BlockCoord> spots = StandSpots.of(cell, safetyView(), spiritCoord());
            if (spots.isEmpty() && standView.floorUnder(cell) && standView.allowedStand(cell)) {
                spots = List.of(cell);
            }

            if (spots.isEmpty()) {
                if (tally.noStandCount() < NO_STAND_LOGGED) {
                    BlockPos at = toPos(cell);
                    LOGGER.info(
                            "[fill] {}: нет стоянки у {}: под ней {}, в ней {}, над ней {}, верх без листвы y={}",
                            spirit.getName().getString(),
                            cell,
                            level.getBlockState(at.below()).getBlock(),
                            level.getBlockState(at).getBlock(),
                            level.getBlockState(at.above()).getBlock(),
                            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.x(), cell.z()));
                }
                tally.noStand();
                tally.skipped();
                brokenY = Math.min(brokenY, cell.y());
                release(cell);
                cursor++;
                continue;
            }
            spots = spots.stream().filter(standView::allowedStand).toList();
            if (spots.isEmpty()) {
                tally.noStand();
                tally.skipped();
                brokenY = Math.min(brokenY, cell.y());
                release(cell);
                cursor++;
                continue;
            }
            BlockCoord stand = spots.get(0);
            currentStand = stand;
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
            state = State.APPROACH;
            return;
        }
        endPass();
    }

    private void endPass() {
        int done = passPlaced;
        if (done > 0 && replan()) {
            return;
        }
        state = State.DONE;
        finish();
    }

    private void pillarTick() {
        switch (pillar.tick()) {
            case RUNNING -> {}
            case BUILT -> {
                passPlaced++;
                tally.placed(blockId);
                releaseAndAdvance();
                state = State.PICK;
            }
            case FAILED -> {
                LOGGER.info(
                        "[fill] {}: столб в {} не вышел: {}",
                        spirit.getName().getString(),
                        current,
                        pillar.failureReason());
                tally.skipped();
                brokenY = Math.min(brokenY, current.y());
                releaseAndAdvance();
                state = State.PICK;
            }
        }
    }

    private void approachTick() {
        BlockPos feet = spirit.blockPosition();
        if (currentStand != null
                && feet.getX() == currentStand.x()
                && feet.getZ() == currentStand.z()
                && Math.abs(feet.getY() - currentStand.y()) <= 1
                && (spirit.onGround() || spirit.isInWater())) {
            approach.cancel();
            placeCurrent();
            return;
        }
        if (current != null && standView.inReach(current)) {
            approach.cancel();
            placeCurrent();
            return;
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> placeCurrent();
            case STUCK, TIMEOUT -> {
                tally.noStand();
                tally.skipped();
                brokenY = Math.min(brokenY, current == null ? Integer.MAX_VALUE : current.y());
                releaseAndAdvance();
                state = State.PICK;
            }
        }
    }

    private void placeCurrent() {
        if (current.equals(spiritCoord())) {
            pillar = standView.pillarHere(current);
            state = State.PILLAR;
            return;
        }
        BlockPos pos = toPos(current);
        BlockPlacer.Begin begin = placer.begin(pos, wanted, blockId);
        if (begin instanceof BlockPlacer.Started) {
            passPlaced++;
            tally.placed(blockId);
            releaseAndAdvance();
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockPlacer.Protected single) {
            tally.protectedSkipped();
            tally.skipped();
            brokenY = Math.min(brokenY, current.y());
            releaseAndAdvance();
            state = State.PICK;
            if (tally.placedCount() == 0 && cursor >= order.size()) {
                fail("строить запрещено: " + single.reason() + "; поставлено 0 из " + order.size());
            }
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        switch (refused.reason()) {
            case NO_MATERIAL -> {
                tally.failed();
                releaseAndAdvance();
                int have = tally.placedCount() + BlockItems.countIn(spirit.getInventory(), blockId);
                tally.supplies(have, Math.max(order.size() - tally.prebuiltCount() - have, 0));
                fail("не хватило материала '" + blockId + "': нужно " + order.size() + ", есть " + have
                        + "; поставлено " + tally.placedCount() + " из " + order.size());
            }
            case OCCUPIED, NO_SUPPORT -> {
                if (tally.skippedCount() < NO_STAND_LOGGED) {
                    LOGGER.info(
                            "[fill] {}: {} не поставить: {} (дух в {})",
                            spirit.getName().getString(),
                            current,
                            refused.message(),
                            spirit.blockPosition().toShortString());
                }
                tally.skipped();
                brokenY = Math.min(brokenY, current.y());
                releaseAndAdvance();
                state = State.PICK;
            }
            case CHUNK_NOT_LOADED -> {
                if (tally.skippedCount() < NO_STAND_LOGGED) {
                    LOGGER.info(
                            "[fill] {}: {} не поставить: {} (дух в {})",
                            spirit.getName().getString(),
                            current,
                            refused.message(),
                            spirit.blockPosition().toShortString());
                }
                tally.skipped();
                brokenY = Math.min(brokenY, current.y());
                releaseAndAdvance();
                state = State.PICK;
            }
            case OUT_OF_REACH -> {
                tally.noStand();
                tally.skipped();
                brokenY = Math.min(brokenY, current.y());
                releaseAndAdvance();
                state = State.PICK;
            }
        }
    }

    private void finish() {
        if (outcome != Outcome.RUNNING) {
            return;
        }
        releaseAll();
        if (tally.failedCount() > 0) {
            return;
        }
        if (terrain.complete()) {
            tally.supplies(tally.placedCount(), 0);
            outcome = Outcome.SUCCEEDED;
            return;
        }
        fail(tally.summary(region.size()));
    }

    private boolean replan() {
        order.clear();
        order.addAll(terrain.plan(spiritCoord()).cells());
        cursor = 0;
        current = null;
        currentStand = null;
        pillar = null;
        brokenY = Integer.MAX_VALUE;
        passPlaced = 0;
        return !order.isEmpty();
    }

    private Outcome fail(String reason) {
        LOGGER.info(
                "[fill] {}: {} клеток {} от {} не засыпаны: {}",
                spirit.getName().getString(),
                blockId,
                region.size(),
                region.stream().min(Comparator.naturalOrder()).orElse(null),
                reason);
        failureReason = reason;
        outcome = Outcome.FAILED;
        cancel();
        return outcome;
    }

    private SafetyView safetyView() {
        return safety;
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private void release(BlockCoord cell) {
        reservations.release(cell, spirit.getUUID());
    }

    private void releaseAndAdvance() {
        if (current != null) {
            release(current);
            current = null;
        }
        cursor++;
    }

    private void releaseAll() {
        reservations.releaseAll(spirit.getUUID());
        current = null;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
