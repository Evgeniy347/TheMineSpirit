package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.Move;
import com.theminespirit.core.inventory.Slice;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.TransferPlan;
import com.theminespirit.core.inventory.TransferPlanner;
import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class TransferRunner {
    public static final int TRANSFER_MOVES_PER_TICK = 4;

    public enum State {
        RUNNING,
        DONE,
        FAILED,
        CANCELLED
    }

    public enum Shortage {
        NONE,
        SOURCE_LACK,
        DEST_FULL
    }

    private final Container from;
    private final Container to;
    private final IdFilter filter;
    private final int requested;
    private final Map<Integer, Integer> sliceRemaining;

    private final Deque<Move> moves = new ArrayDeque<>();
    private final Deque<Snapshot> expected = new ArrayDeque<>();
    private TransferPlan lastPlan;
    private boolean replanned;
    private State state = State.RUNNING;
    private String failureReason;
    private int moved;
    private int maxMovesPerTick;

    private TransferRunner(Container from, Container to, IdFilter filter, int requested,
            Map<Integer, Integer> sliceRemaining) {
        this.from = from;
        this.to = to;
        this.filter = filter;
        this.requested = requested;
        this.sliceRemaining = sliceRemaining;
    }

    public static TransferRunner filtered(Container from, Container to, IdFilter filter, int maxCount) {
        return new TransferRunner(from, to, filter, maxCount, new HashMap<>());
    }

    public static TransferRunner slices(Container from, Container to, List<Slice> slices) {
        Map<Integer, Integer> remaining = new HashMap<>();
        int total = 0;
        for (Slice slice : slices) {
            remaining.merge(slice.slot(), slice.count(), Integer::sum);
            total += slice.count();
        }
        return new TransferRunner(from, to, IdFilter.matchAll(), total, remaining);
    }

    public State tick() {
        if (state != State.RUNNING) {
            return state;
        }
        if (lastPlan == null) {
            takePlan(remaining());
        }
        int applied = 0;
        while (applied < TRANSFER_MOVES_PER_TICK && !moves.isEmpty()) {
            Move move = moves.peek();
            Snapshot want = expected.peek();
            if (!matches(move, want)) {
                if (replanned) {
                    return fail("содержимое контейнера изменилось во время переноса: слот источника уже "
                            + "не содержит ожидаемое, перенесено " + moved + " из " + requested);
                }
                replanned = true;
                takePlan(remaining());
                continue;
            }
            apply(move);
            moves.poll();
            expected.poll();
            applied++;
            moved += move.count();
        }
        maxMovesPerTick = Math.max(maxMovesPerTick, applied);
        if (moves.isEmpty()) {
            state = State.DONE;
        }
        return state;
    }

    public void cancel() {
        if (state == State.RUNNING) {
            state = State.CANCELLED;
        }
    }

    public State state() {
        return state;
    }

    public String failureReason() {
        return failureReason;
    }

    public int moved() {
        return moved;
    }

    public int requested() {
        return requested;
    }

    public int remaining() {
        return requested - moved;
    }

    public int maxMovesPerTick() {
        return maxMovesPerTick;
    }

    public Shortage shortage() {
        if (remaining() <= 0) {
            return Shortage.NONE;
        }
        return sourceAvailable() >= remaining() ? Shortage.DEST_FULL : Shortage.SOURCE_LACK;
    }

    private void takePlan(int count) {
        StackMapper mapper = new StackMapper();
        List<Stack> fromStacks = mapper.map(from);
        List<Stack> toStacks = mapper.map(to);
        TransferPlan plan;
        if (sliceRemaining.isEmpty()) {
            plan = TransferPlanner.plan(fromStacks, toStacks, filter, count);
        } else {
            plan = TransferPlanner.planSlices(fromStacks, toStacks, remainingSlices());
        }
        lastPlan = plan;
        moves.clear();
        expected.clear();
        moves.addAll(plan.moves());
        for (Move move : plan.moves()) {
            Stack source = fromStacks.get(move.fromSlot());
            expected.add(new Snapshot(source.item(), mapper.sampleFor(source.variant()), move.count()));
        }
    }

    private List<Slice> remainingSlices() {
        return sliceRemaining.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new Slice(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean matches(Move move, Snapshot want) {
        ItemStack current = from.getItem(move.fromSlot());
        if (current.isEmpty() || current.getCount() < want.count()
                || !StackMapper.itemId(current).equals(want.item())
                || !ItemStack.isSameItemSameComponents(want.sample(), current)) {
            return false;
        }
        ItemStack target = to.getItem(move.toSlot());
        if (target.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(want.sample(), target)
                && target.getCount() + move.count() <= target.getMaxStackSize();
    }

    private void apply(Move move) {
        ItemStack source = from.getItem(move.fromSlot());
        ItemStack taken = source.split(move.count());
        ItemStack target = to.getItem(move.toSlot());
        if (target.isEmpty()) {
            to.setItem(move.toSlot(), taken);
        } else {
            target.grow(taken.getCount());
        }
        from.setChanged();
        to.setChanged();
        sliceRemaining.computeIfPresent(move.fromSlot(), (slot, left) -> left - move.count());
    }

    private int sourceAvailable() {
        if (sliceRemaining.isEmpty()) {
            StackMapper mapper = new StackMapper();
            int available = 0;
            for (Stack stack : mapper.map(from)) {
                if (stack != null && filter.matches(stack.item())) {
                    available += stack.count();
                }
            }
            return available;
        }
        StackMapper mapper = new StackMapper();
        List<Stack> stacks = mapper.map(from);
        int available = 0;
        for (Map.Entry<Integer, Integer> entry : sliceRemaining.entrySet()) {
            if (entry.getKey() < stacks.size()) {
                Stack stack = stacks.get(entry.getKey());
                if (stack != null) {
                    available += Math.min(stack.count(), entry.getValue());
                }
            }
        }
        return available;
    }

    private State fail(String reason) {
        state = State.FAILED;
        failureReason = reason;
        return state;
    }

    private record Snapshot(String item, ItemStack sample, int count) {
    }
}
