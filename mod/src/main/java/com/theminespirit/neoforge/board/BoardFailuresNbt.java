package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.graph.GraphFailureMemory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

final class BoardFailuresNbt {
    static final String KEY = "Failures";
    private static final String NODE_KEY = "Node";
    private static final String TARGET_KEY = "Target";
    private static final String NEED_KEY = "Need";
    private static final String KIND_KEY = "Kind";
    private static final String ITEM_KEY = "Item";
    private static final String COUNT_NEED_KEY = "Count";
    private static final String TOOL_KEY = "Tool";
    private static final String RANK_KEY = "Rank";
    private static final String SPIRIT_KEY = "Spirit";
    private static final String BUILDING_KEY = "Building";
    private static final String POINT_KEY = "Point";
    private static final String STUCK_KEY = "Stuck";
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
            if (fact.need() != null) {
                item.put(NEED_KEY, needTag(fact.need()));
            }
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
                    item.contains(NEED_KEY) ? needOf(item.getCompound(NEED_KEY)) : null,
                    item.getLong(LAST_TICK_KEY),
                    ticks(item.getLongArray(TICKS_KEY)),
                    item.getInt(COUNT_KEY)));
        }
        return List.copyOf(saved);
    }

    private static CompoundTag needTag(GraphBlock.Need need) {
        CompoundTag tag = new CompoundTag();
        if (need instanceof GraphBlock.Item item) {
            tag.putString(KIND_KEY, "item");
            tag.putString(ITEM_KEY, item.item());
            tag.putInt(COUNT_NEED_KEY, item.count());
        } else if (need instanceof GraphBlock.Tool tool) {
            tag.putString(KIND_KEY, "tool");
            tag.putString(TOOL_KEY, tool.kind());
            tag.putInt(RANK_KEY, tool.rank());
            tag.putString(SPIRIT_KEY, tool.spirit());
        } else if (need instanceof GraphBlock.Building building) {
            tag.putString(KIND_KEY, "building");
            tag.putString(BUILDING_KEY, building.id());
        } else if (need instanceof GraphBlock.Path path) {
            tag.putString(KIND_KEY, "path");
            tag.putString(POINT_KEY, path.point());
        } else if (need instanceof GraphBlock.Holder holder) {
            tag.putString(KIND_KEY, "holder");
            tag.putString(SPIRIT_KEY, holder.spirit());
            tag.putBoolean(STUCK_KEY, holder.stuck());
        } else if (need instanceof GraphBlock.Impossible impossible) {
            tag.putString(KIND_KEY, "impossible");
            tag.putString(NODE_KEY, impossible.node());
        } else {
            throw new IllegalStateException("Неизвестная нужда сохранённого провала: " + need);
        }
        return tag;
    }

    private static GraphBlock.@Nullable Need needOf(CompoundTag tag) {
        return switch (tag.getString(KIND_KEY)) {
            case "item" -> new GraphBlock.Item(tag.getString(ITEM_KEY), tag.getInt(COUNT_NEED_KEY));
            case "tool" ->
                new GraphBlock.Tool(tag.getString(TOOL_KEY), tag.getInt(RANK_KEY), tag.getString(SPIRIT_KEY));
            case "building" -> new GraphBlock.Building(tag.getString(BUILDING_KEY));
            case "path" -> new GraphBlock.Path(tag.getString(POINT_KEY));
            case "holder" -> new GraphBlock.Holder(tag.getString(SPIRIT_KEY), tag.getBoolean(STUCK_KEY));
            case "impossible" -> new GraphBlock.Impossible(tag.getString(NODE_KEY));
            default -> null;
        };
    }

    private static List<Long> ticks(long[] ticks) {
        List<Long> saved = new ArrayList<>(ticks.length);
        for (long tick : ticks) {
            saved.add(tick);
        }
        return saved;
    }
}
