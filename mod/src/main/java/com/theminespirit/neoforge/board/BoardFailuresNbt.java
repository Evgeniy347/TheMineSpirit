package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFailureMemory;
import com.theminespirit.core.board.graph.GraphFeedback;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class BoardFailuresNbt {
    static final String KEY = "Failures";
    private static final String NODE_KEY = "Node";
    private static final String TARGET_KEY = "Target";
    private static final String CAUSE_KEY = "Cause";
    private static final String LAST_TICK_KEY = "LastTick";
    private static final String TICKS_KEY = "Ticks";
    private static final String COUNT_KEY = "Count";

    private BoardFailuresNbt() {}

    static ListTag write(List<GraphFailureMemory.Saved> saved) {
        ListTag list = new ListTag();
        for (GraphFailureMemory.Saved fact : saved) {
            CompoundTag item = new CompoundTag();
            item.putString(NODE_KEY, fact.node());
            item.putString(TARGET_KEY, fact.target());
            item.putString(CAUSE_KEY, fact.cause().name());
            item.putLong(LAST_TICK_KEY, fact.lastTick());
            item.putLongArray(
                    TICKS_KEY, fact.ticks().stream().mapToLong(Long::longValue).toArray());
            item.putInt(COUNT_KEY, fact.count());
            list.add(item);
        }
        return list;
    }

    static List<GraphFailureMemory.Saved> read(CompoundTag tag) {
        ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        List<GraphFailureMemory.Saved> saved = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag item = list.getCompound(index);
            saved.add(new GraphFailureMemory.Saved(
                    item.getString(NODE_KEY),
                    item.getString(TARGET_KEY),
                    cause(item.getString(CAUSE_KEY)),
                    item.getLong(LAST_TICK_KEY),
                    ticks(item.getLongArray(TICKS_KEY)),
                    item.getInt(COUNT_KEY)));
        }
        return List.copyOf(saved);
    }

    private static GraphFeedback.Cause cause(String name) {
        try {
            return GraphFeedback.Cause.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException("Причина сохранённого провала '" + name
                    + "' неизвестна этой версии мода: память неудач не читается, проверьте сборку");
        }
    }

    private static List<Long> ticks(long[] ticks) {
        List<Long> saved = new ArrayList<>(ticks.length);
        for (long tick : ticks) {
            saved.add(tick);
        }
        return saved;
    }
}
