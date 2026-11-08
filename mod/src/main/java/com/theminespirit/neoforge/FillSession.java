package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotProtection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class FillSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LONG_STATE_TICKS = 200;

    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    enum State {
        EXIT,
        PICK,
        APPROACH,
        PILLAR,
        DONE
    }

    final SpiritEntity spirit;
    final Level level;
    final String blockId;
    final BlockState wanted;
    final Set<BlockCoord> region;
    int stateTicks;

    @Nullable
    State lastState;

    @Nullable
    BlockCoord currentStand;

    final List<BlockCoord> order = new ArrayList<>();
    final LevelSafetyView safety;
    final FillStands standView;
    final FillTerrain terrain;
    final BlockPlacer placer;
    final Approach approach;
    final BuildReservations reservations;

    State state = State.PICK;
    Outcome outcome = Outcome.RUNNING;
    String failureReason;
    int cursor;
    int brokenY = Integer.MAX_VALUE;
    int passPlaced;
    BlockCoord current;
    final FillTally tally = new FillTally();

    @Nullable
    PillarColumn pillar;

    final FillReport report;
    private final FillExitState exitState;
    private final FillPickState pickState;
    private final FillApproachState approachState;
    private final FillPillarState pillarState;

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
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.NONE);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.report = new FillReport(this);
        this.exitState = new FillExitState(this);
        this.pickState = new FillPickState(this);
        this.approachState = new FillApproachState(this);
        this.pillarState = new FillPillarState(this);
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
            return report.fail("дух исчез во время стройки");
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
            case EXIT -> exitState.tick();
            case PICK -> pickState.tick();
            case APPROACH -> approachState.tick();
            case PILLAR -> pillarState.tick();
            case DONE -> report.finish();
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
        return report.outcome();
    }

    public String failureReason() {
        return report.failureReason();
    }

    public ScriptInstance.Progress progress() {
        return report.progress();
    }

    public int placed() {
        return report.placed();
    }

    public int skipped() {
        return report.skipped();
    }

    public String blockId() {
        return blockId;
    }

    public Map<String, Object> resultData() {
        return report.resultData();
    }

    public int blockReads() {
        return report.blockReads();
    }

    public Map<String, Object> saveState() {
        return report.saveState();
    }

    public void restoreState(Map<String, Object> saved) {
        report.restoreState(saved);
    }

    void placeCurrent() {
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
                report.fail("строить запрещено: " + single.reason() + "; поставлено 0 из " + order.size());
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
                report.fail("не хватило материала '" + blockId + "': нужно " + order.size() + ", есть " + have
                        + "; поставлено " + tally.placedCount() + " из " + order.size());
            }
            case OCCUPIED, NO_SUPPORT -> {
                if (tally.skippedCount() < FillPickState.NO_STAND_LOGGED) {
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
                if (tally.skippedCount() < FillPickState.NO_STAND_LOGGED) {
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

    boolean replan() {
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

    SafetyView safetyView() {
        return safety;
    }

    BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    void release(BlockCoord cell) {
        reservations.release(cell, spirit.getUUID());
    }

    void releaseAndAdvance() {
        if (current != null) {
            release(current);
            current = null;
        }
        cursor++;
    }

    void releaseAll() {
        reservations.releaseAll(spirit.getUUID());
        current = null;
    }

    static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
