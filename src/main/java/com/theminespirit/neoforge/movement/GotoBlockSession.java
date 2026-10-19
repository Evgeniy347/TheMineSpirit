package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.Goal;
import com.theminespirit.core.movement.GoalComposite;
import com.theminespirit.core.movement.GoalNear;
import com.theminespirit.core.movement.MoveCost;
import com.theminespirit.core.movement.StandChoice;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.BlockSets;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class GotoBlockSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public static final int MAX_SLICES_PER_TICK = 4;
    public static final int NEAR_RADIUS = 8;
    public static final double WALK_ARRIVAL = 2.0;

    private enum State {
        FIND,
        WALK
    }

    private final SpiritEntity spirit;
    private final String block;
    private final int radius;
    private final int count;
    private final BlockLocator.Search search;
    private final Approach approach;
    private final List<BlockCoord> found = new ArrayList<>();
    private List<BlockCoord> ordered = List.of();
    private Goal goal;
    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private int next;
    private BlockCoord current;
    private int slices;
    private int refused;
    private int notReached;

    public GotoBlockSession(SpiritEntity spirit, String block, int radius, int count) {
        this.spirit = spirit;
        this.block = block;
        this.radius = radius;
        this.count = count;
        this.search = BlockLocator.search(
                spirit.level(), spirit.blockPosition(), radius, BlockSets.resolve(IdFilter.of(List.of(block))));
        this.approach = new Approach(spirit);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез по дороге к блоку " + block);
        }
        switch (state) {
            case FIND -> find();
            case WALK -> walk();
        }
        return outcome;
    }

    public void cancel() {
        approach.cancel();
    }

    public void suspend() {
        approach.suspend();
    }

    public String failureReason() {
        return failureReason;
    }

    public int slicesUsed() {
        return slices;
    }

    public int maxSlicesPerTick() {
        return search.maxSlicesPerTick();
    }

    private void find() {
        for (int i = 0; i < MAX_SLICES_PER_TICK && !search.done(); i++) {
            slices++;
            for (BlockPos pos : search.step(1)) {
                found.add(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        if (!search.done()) {
            return;
        }
        if (found.isEmpty()) {
            fail("блоков вида " + block + " в радиусе " + radius + " нет");
            return;
        }
        BlockPos feetPos = spirit.blockPosition();
        BlockCoord feet = new BlockCoord(feetPos.getX(), feetPos.getY(), feetPos.getZ());
        ordered = found.stream()
                .sorted(Comparator.comparingInt((BlockCoord candidate) -> MoveCost.walkTicks(feet, candidate))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(count)
                .toList();
        goal = new GoalComposite(ordered.stream()
                .<Goal>map(candidate -> new GoalNear(candidate, NEAR_RADIUS))
                .toList());
        state = State.WALK;
    }

    private void walk() {
        if (reachedAny()) {
            succeed();
            return;
        }
        if (current == null && !pickNext()) {
            return;
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                if (reachedAny()) {
                    succeed();
                } else {
                    notReached++;
                    current = null;
                }
            }
            case STUCK, TIMEOUT -> {
                notReached++;
                current = null;
            }
        }
    }

    private boolean pickNext() {
        while (next < ordered.size()) {
            BlockCoord candidate = ordered.get(next++);
            if (TravelCheck.refusal(spirit, candidate) != null) {
                refused++;
                continue;
            }
            approach.begin(new Vec3(candidate.x() + 0.5, candidate.y(), candidate.z() + 0.5), WALK_ARRIVAL);
            current = candidate;
            return true;
        }
        fail("к блокам вида " + block + " не подойти: ближайших взято " + ordered.size() + ", отказано зоной: "
                + refused + ", не дошёл: " + notReached);
        return false;
    }

    private boolean reachedAny() {
        BlockPos feetPos = spirit.blockPosition();
        if (!goal.isSatisfied(new BlockCoord(feetPos.getX(), feetPos.getY(), feetPos.getZ()))) {
            return false;
        }
        for (BlockCoord candidate : ordered) {
            if (inReach(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean inReach(BlockCoord candidate) {
        Vec3 eye = spirit.position().add(0, StandChoice.EYE_HEIGHT, 0);
        double dx = eye.x - (candidate.x() + 0.5);
        double dy = eye.y - (candidate.y() + 0.5);
        double dz = eye.z - (candidate.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= StandChoice.REACH_DISTANCE * StandChoice.REACH_DISTANCE;
    }

    private void succeed() {
        approach.cancel();
        outcome = Outcome.SUCCEEDED;
    }

    private Outcome fail(String reason) {
        approach.cancel();
        failureReason = reason;
        outcome = Outcome.FAILED;
        return outcome;
    }
}
