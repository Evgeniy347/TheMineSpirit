package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ClearAreaSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    protected enum State {
        PICK,
        APPROACH,
        BREAK,
        COLLECT
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 4;

    protected final SpiritEntity spirit;
    protected final Level level;
    protected final BuildBox box;
    protected final List<BlockCoord> remaining;
    protected final LevelSafetyView safety;
    protected final BlockBreaker breaker;
    protected final Approach approach;
    protected final BuildReservations reservations;

    protected State state = State.PICK;
    protected Outcome outcome = Outcome.RUNNING;
    protected String failureReason;
    protected BlockCoord currentTarget;
    protected DropCollector collector;

    protected int removed;
    protected int unsafe;
    protected int noStand;
    protected int protectedSkipped;
    protected int foreignSkipped;
    protected int unbreakable;
    private int entityQueries;
    private int blockReads;

    public ClearAreaSession(SpiritEntity spirit, BuildBox box) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.box = box;
        this.remaining = new ArrayList<>(box.cellsBottomUp());
        System.out.println("DIAG сессия коробка=" + box + " клеток=" + remaining.size() + " первая="
                + (remaining.isEmpty() ? "нет" : remaining.get(0)) + " мир=" + level.dimension().location()
                + " дух=" + spirit.blockPosition().toShortString());
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время расчистки");
        }
        switch (state) {
            case PICK -> pick();
            case APPROACH -> approachTick();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        reservations.releaseAll(spirit.getUUID());
    }

    public void suspend() {
        approach.suspend();
        if (collector != null) {
            collector.suspend();
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        int total = Math.max(boxVolume(), 1);
        return new ScriptInstance.Progress(Math.min(removed + skippedTotal(), total), total);
    }

    public int removed() {
        return removed;
    }

    public int skippedTotal() {
        return unsafe + noStand + protectedSkipped + foreignSkipped + unbreakable;
    }

    public Map<String, Object> saveState() {
        return new BuildState(0, removed, skippedTotal(), 0, outcome == Outcome.RUNNING).toMap();
    }

    public void restoreState(Map<String, Object> saved) {
        BuildState state = BuildState.fromMap(saved);
        removed = Math.max(removed, state.removed());
    }

    protected int boxVolume() {
        return (int) Math.min(box.volume(), Integer.MAX_VALUE);
    }

    private void pick() {
        if (remaining.size() == (int) Math.min(box.volume(), Integer.MAX_VALUE)) {
            StringBuilder grid = new StringBuilder();
            for (int y = 0; y <= 3; y++) {
                for (int x = 3; x <= 6; x++) {
                    for (int z = 3; z <= 6; z++) {
                        grid.append(level.getBlockState(new BlockPos(box.minX() + x - 3, box.minY() + y - 1,
                                box.minZ() + z - 3)).getBlock().toString().replace("Block{minecraft:", "")
                                .replace("}", "")).append(',');
                    }
                }
                grid.append(';');
            }
            System.out.println("DIAG pick сетка=" + grid + " дух=" + spirit.blockPosition().toShortString()
                    + " клеток=" + remaining.size());
        }
        if (remaining.size() == (int) Math.min(box.volume(), Integer.MAX_VALUE)) {
            BlockPos feet = spirit.blockPosition();
            BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
            System.out.println("DIAG pick планёр=" + MiningPlanner.next(from, new ArrayList<>(remaining), safety));
        }        while (!remaining.isEmpty() && level.getBlockState(toPos(remaining.get(0))).isAir()) {
            remaining.remove(0);
        }
        if (remaining.isEmpty()) {
            finish();
            return;
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        MiningPlanner.Outcome result = MiningPlanner.next(from, remaining, safety);
        if (result instanceof MiningPlanner.Exhausted exhausted) {
            unsafe += exhausted.unsafe();
            noStand += exhausted.noStand();
            remaining.clear();
            finish();
            return;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        remaining.remove(next.target());
        if (reservations.foreign(next.target(), spirit.getUUID())) {
            foreignSkipped++;
            return;
        }
        reservations.reserve(next.target(), spirit.getUUID());
        currentTarget = next.target();
        approach.begin(new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5), APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> prepareBreak();
            case STUCK, TIMEOUT -> {
                noStand++;
                releaseCurrent();
                state = State.PICK;
            }
        }
    }

    private void prepareBreak() {
        if (!(MiningSafety.canBreak(currentTarget, safety) instanceof BreakSafety.Safe)) {
            unsafe++;
            releaseCurrent();
            state = State.PICK;
            return;
        }
        BlockBreaker.Begin begin = breaker.begin(toPos(currentTarget));
        if (begin instanceof BlockBreaker.Protected) {
            protectedSkipped++;
            releaseCurrent();
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused) {
            unbreakable++;
            releaseCurrent();
            state = State.PICK;
            return;
        }
        state = State.BREAK;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {
            }
            case BROKEN -> {
                blockReads++;
                removed++;
                collector = new DropCollector(spirit, IdFilter.matchAll(), currentTarget, COLLECT_RADIUS,
                        PickupTuning.DEFAULT, OverflowMode.BY_VALUE);
                state = State.COLLECT;
            }
            case GONE -> {
                releaseCurrent();
                state = State.PICK;
            }
        }
    }

    private void collectTick() {
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        entityQueries += collector.entityQueries();
        collector = null;
        if (collected == DropCollector.State.FAILED) {
            fail("не удалось подобрать дроп у " + currentTarget + ": расчистка остановлена");
            return;
        }
        releaseCurrent();
        state = State.PICK;
    }

    private void finish() {
        reservations.releaseAll(spirit.getUUID());
        if (outcome != Outcome.RUNNING) {
            return;
        }
        if (removed == 0) {
            fail(summary() + "; не убран ни один блок");
            return;
        }
        outcome = Outcome.SUCCEEDED;
    }

    protected Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        cancel();
        return outcome;
    }

    private String summary() {
        return "убрано " + removed + " из " + boxVolume() + "; пропущено: " + skippedTotal() + " (небезопасных: " + unsafe
                + ", без стоянки: " + noStand + ", защищено: " + protectedSkipped + ", неразрушимых: " + unbreakable
                + ", занято другим духом: " + foreignSkipped + ")";
    }

    private void releaseCurrent() {
        if (currentTarget != null) {
            reservations.release(currentTarget, spirit.getUUID());
            currentTarget = null;
        }
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
