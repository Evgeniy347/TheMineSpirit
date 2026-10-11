package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TransferPlanner {
    private TransferPlanner() {
    }

    public static TransferPlan plan(List<Stack> from, List<Stack> to, IdFilter filter, int maxCount) {
        Objects.requireNonNull(from, "Слоты источника не могут быть null: переносить нечего");
        Objects.requireNonNull(to, "Слоты приёмника не могут быть null: некуда переносить");
        Objects.requireNonNull(filter, "Фильтр предметов не может быть null: "
                + "без фильтра непонятно, какие предметы идут в контейнер");
        if (maxCount < 0) {
            throw new IllegalArgumentException("Лимит переноса " + maxCount
                    + " отрицательный: лимит задаёт, сколько предметов переносим за один вызов");
        }
        Receiver receiver = new Receiver(to);
        List<Move> moves = new ArrayList<>();
        int moved = 0;
        int available = 0;
        int left = maxCount;
        for (int slot = 0; slot < from.size(); slot++) {
            Stack source = from.get(slot);
            if (source == null || !filter.matches(source.item())) {
                continue;
            }
            available += source.count();
            int inSlot = source.count();
            while (inSlot > 0 && left > 0) {
                int target = receiver.slotFor(source);
                if (target < 0) {
                    break;
                }
                int count = Math.min(Math.min(receiver.roomIn(target, source), inSlot), left);
                receiver.place(target, source, count);
                moves.add(new Move(slot, target, count));
                moved += count;
                left -= count;
                inSlot -= count;
            }
        }
        return new TransferPlan(moves, moved, available - moved);
    }

    private static String key(Stack stack) {
        return stack.item() + "|" + stack.variant();
    }

    private static final class Receiver {
        private final int size;
        private final int[] room;
        private final boolean[] emptySlot;
        private final Map<String, List<Integer>> partialByKind;
        private final Map<String, Integer> cursorByKind;
        private int nextEmpty;

        private Receiver(List<Stack> slots) {
            this.size = slots.size();
            this.room = new int[size];
            this.emptySlot = new boolean[size];
            this.partialByKind = new HashMap<>();
            this.cursorByKind = new HashMap<>();
            this.nextEmpty = 0;
            for (int slot = 0; slot < size; slot++) {
                Stack stack = slots.get(slot);
                this.emptySlot[slot] = stack == null;
                this.room[slot] = stack == null ? 0 : stack.freeSpace();
                if (stack != null && stack.freeSpace() > 0) {
                    partialByKind.computeIfAbsent(key(stack), kind -> new ArrayList<>()).add(slot);
                }
            }
        }

        private int slotFor(Stack source) {
            String kind = key(source);
            List<Integer> partial = partialByKind.get(kind);
            if (partial != null) {
                int cursor = cursorByKind.getOrDefault(kind, 0);
                while (cursor < partial.size()) {
                    int slot = partial.get(cursor);
                    if (room[slot] > 0) {
                        cursorByKind.put(kind, cursor);
                        return slot;
                    }
                    cursor++;
                }
                cursorByKind.put(kind, cursor);
            }
            while (nextEmpty < size && !emptySlot[nextEmpty]) {
                nextEmpty++;
            }
            return nextEmpty < size ? nextEmpty : -1;
        }

        private int roomIn(int target, Stack source) {
            return emptySlot[target] ? source.maxStack() : room[target];
        }

        private void place(int target, Stack source, int count) {
            if (emptySlot[target]) {
                room[target] = source.maxStack() - count;
                emptySlot[target] = false;
                if (room[target] > 0) {
                    partialByKind.computeIfAbsent(key(source), kind -> new ArrayList<>()).add(target);
                }
                return;
            }
            room[target] -= count;
        }
    }
}
