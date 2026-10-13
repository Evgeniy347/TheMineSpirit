package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildOrder;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FillSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        EXIT,
        PICK,
        APPROACH,
        DONE
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final String blockId;
    private final BlockState wanted;
    private final BuildBox box;
    private final List<BlockCoord> order;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;

    private final Set<BlockCoord> spiritCells;
    private boolean noExit;

    private State state = State.PICK;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private int cursor;
    private BlockCoord current;
    private int placed;
    private int skipped;
    private int failed;
    private int noStand;
    private int protectedSkipped;
    private int foreignSkipped;
    private int blockReads;
    private int slices;

    public FillSession(SpiritEntity spirit, String blockId, BuildBox box) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.blockId = blockId;
        this.wanted = BlockStates.stateFor(blockId);
        this.box = box;
        this.safety = new LevelSafetyView(level);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        Set<BlockCoord> occupied = spiritCells();
        this.spiritCells = Set.copyOf(occupied);
        this.order = BuildOrder.placement(box.cellsBottomUp(), occupied);
        if (insideBox(spirit.blockPosition()) && !occupied.isEmpty()) {
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
        switch (state) {
            case EXIT -> exitTick();
            case PICK -> pick();
            case APPROACH -> approachTick();
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
        return placed;
    }

    public int skipped() {
        return skipped;
    }

    public String blockId() {
        return blockId;
    }

    public int blockReads() {
        return blockReads;
    }

    public int safetyReads() {
        return safety.reads();
    }

    public Map<String, Object> saveState() {
        return new BuildState(placed, 0, skipped, failed, outcome == Outcome.RUNNING).toMap();
    }

    public void restoreState(Map<String, Object> saved) {
        BuildState state = BuildState.fromMap(saved);
        placed = Math.max(placed, state.placed());
        skipped = Math.max(skipped, state.skipped());
        failed = Math.max(failed, state.failed());
    }

    private void exitTick() {
        BlockCoord exit = exitStand();
        if (exit == null) {
            noExit = true;
            state = State.PICK;
            return;
        }
        if (!approach.isActive()) {
            approach.begin(new Vec3(exit.x() + 0.5, exit.y(), exit.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        }
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> state = State.PICK;
            case STUCK, TIMEOUT -> state = State.PICK;
        }
    }

    private void pick() {
        while (cursor < order.size()) {
            BlockCoord cell = order.get(cursor);
            if (noExit && spiritCells.contains(cell)) {
                skipped++;
                cursor++;
                continue;
            }
            BlockPos pos = toPos(cell);
            blockReads++;
            BlockState current = level.getBlockState(pos);
            if (current.is(wanted.getBlock())) {
                cursor++;
                continue;
            }
            if (reservations.foreign(cell, spirit.getUUID())) {
                foreignSkipped++;
                skipped++;
                cursor++;
                continue;
            }
            reservations.reserve(cell, spirit.getUUID());
            this.current = cell;
            List<BlockCoord> stands = StandSpots.of(cell, safetyView(), spiritCoord());
            slices++;
            if (stands.isEmpty()) {
                noStand++;
                skipped++;
                release(cell);
                cursor++;
                continue;
            }
            BlockCoord stand = stands.get(0);
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
            state = State.APPROACH;
            return;
        }
        state = State.DONE;
        finish();
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> placeCurrent();
            case STUCK, TIMEOUT -> {
                noStand++;
                skipped++;
                releaseAndAdvance();
                state = State.PICK;
            }
        }
    }

    private void placeCurrent() {
        BlockPos pos = toPos(current);
        BlockPlacer.Begin begin = placer.begin(pos, wanted, blockId);
        if (begin instanceof BlockPlacer.Started) {
            placed++;
            releaseAndAdvance();
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockPlacer.Protected single) {
            protectedSkipped++;
            skipped++;
            releaseAndAdvance();
            state = State.PICK;
            if (placed == 0 && cursor >= order.size()) {
                fail("строить запрещено: " + single.reason() + "; поставлено 0 из " + order.size());
            }
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        switch (refused.reason()) {
            case NO_MATERIAL -> {
                failed++;
                releaseAndAdvance();
                int have = BlockItems.countIn(spirit.getInventory(), blockId);
                fail("не хватило материала '" + blockId + "': нужно " + order.size() + ", есть " + (placed + have)
                        + "; поставлено " + placed + " из " + order.size());
            }
            case OCCUPIED, NO_SUPPORT, CHUNK_NOT_LOADED -> {
                skipped++;
                releaseAndAdvance();
                state = State.PICK;
            }
            case OUT_OF_REACH -> {
                noStand++;
                skipped++;
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
        if (failed > 0) {
            return;
        }
        if (placed == 0) {
            fail(summary() + "; ни один блок не поставлен");
            return;
        }
        outcome = Outcome.SUCCEEDED;
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        cancel();
        return outcome;
    }

    private String summary() {
        return "поставлено " + placed + " из " + order.size() + "; пропущено: " + skipped + " (без стоянки: " + noStand
                + ", защищено: " + protectedSkipped + ", занято другим духом: " + foreignSkipped + ")";
    }

    private Set<BlockCoord> spiritCells() {
        BlockPos feet = spirit.blockPosition();
        Set<BlockCoord> cells = new HashSet<>();
        BlockCoord legs = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        BlockCoord head = new BlockCoord(feet.getX(), feet.getY() + 1, feet.getZ());
        if (box.contains(legs)) {
            cells.add(legs);
        }
        if (box.contains(head)) {
            cells.add(head);
        }
        return cells;
    }

    private boolean insideBox(BlockPos pos) {
        return box.contains(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
    }

    private BlockCoord exitStand() {
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        List<BlockCoord> candidates = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockCoord cell = new BlockCoord(from.x() + dx, from.y() + dy, from.z() + dz);
                    if (box.contains(cell)) {
                        continue;
                    }
                    if (MiningSafety.canStand(cell, safetyView())) {
                        candidates.add(cell);
                    }
                }
            }
        }
        candidates.sort((first, second) -> {
            long firstDistance = distanceSquared(from, first);
            long secondDistance = distanceSquared(from, second);
            return Long.compare(firstDistance, secondDistance);
        });
        return candidates.isEmpty() ? null : candidates.get(0);
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

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
