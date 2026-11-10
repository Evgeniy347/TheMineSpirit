package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class BoardStateNbt {
    private static final String BLOCKED_KEY = "Blocked";
    private static final String PAUSES_KEY = "Pauses";
    private static final String STUCK_KEY = "Stuck";
    private static final String ID_KEY = "Id";
    private static final String MISSING_KEY = "Missing";
    private static final String AVAILABLE_KEY = "Available";
    private static final String SINCE_KEY = "Since";
    private static final String SPIRIT_KEY = "Spirit";
    private static final String CASE_KEY = "Case";
    private static final String CONSECUTIVE_KEY = "Consecutive";
    private static final String PAUSED_UNTIL_KEY = "PausedUntil";
    private static final String DETACHED_KEY = "Detached";
    private static final String TOKEN_KEY = "Token";
    private static final String AT_KEY = "At";
    private static final String NO_PATH_KEY = "NoPath";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final String BLOCK_KEY = "Block";
    private static final String BLOCK_KIND_KEY = "Kind";
    private static final String BLOCK_NODE_KEY = "Node";
    private static final String BLOCK_TARGET_KEY = "Target";
    private static final String BLOCK_ITEM_KEY = "Item";
    private static final String BLOCK_COUNT_KEY = "Count";
    private static final String BLOCK_TAG_KEY = "Tag";
    private static final String BLOCK_TOOL_KEY = "Tool";
    private static final String BLOCK_RANK_KEY = "Rank";
    private static final String BLOCK_SPIRIT_KEY = "Spirit";
    private static final String BLOCK_BUILDING_KEY = "Building";
    private static final String BLOCK_POINT_KEY = "Point";
    private static final String BLOCK_STUCK_KEY = "Stuck";

    record Parts(List<BoardState.Blocked> blocked, List<BoardState.Pause> pauses, List<BoardState.Stuck> stuck) {
        Parts {
            Objects.requireNonNull(blocked, "Блокировки не могут быть null");
            Objects.requireNonNull(pauses, "Паузы не могут быть null");
            Objects.requireNonNull(stuck, "Метки застревания не могут быть null");
        }
    }

    private BoardStateNbt() {}

    static void write(CompoundTag tag, BoardState state) {
        ListTag blocked = new ListTag();
        for (BoardState.Blocked entry : state.blocked()) {
            CompoundTag item = new CompoundTag();
            item.putString(ID_KEY, entry.ticketId());
            item.put(MISSING_KEY, items(entry.missing()));
            item.put(AVAILABLE_KEY, items(entry.available()));
            item.putLong(SINCE_KEY, entry.since());
            if (entry.block() != null) {
                item.put(BLOCK_KEY, blockTag(entry.block()));
            }
            blocked.add(item);
        }
        tag.put(BLOCKED_KEY, blocked);
        ListTag pauses = new ListTag();
        for (BoardState.Pause entry : state.pauses()) {
            CompoundTag item = new CompoundTag();
            item.putUUID(SPIRIT_KEY, entry.spirit());
            item.putString(CASE_KEY, entry.boardCase().name());
            item.putInt(CONSECUTIVE_KEY, entry.consecutive());
            item.putLong(PAUSED_UNTIL_KEY, entry.pausedUntil());
            item.putBoolean(DETACHED_KEY, entry.detached());
            if (entry.token() != null) {
                item.putString(TOKEN_KEY, entry.token());
            }
            pauses.add(item);
        }
        tag.put(PAUSES_KEY, pauses);
        ListTag stuck = new ListTag();
        for (BoardState.Stuck entry : state.stuck()) {
            CompoundTag item = new CompoundTag();
            item.putUUID(SPIRIT_KEY, entry.spirit());
            item.putLong(SINCE_KEY, entry.since());
            item.putBoolean(NO_PATH_KEY, entry.noPath());
            if (entry.at() != null) {
                CompoundTag at = new CompoundTag();
                at.putInt(X_KEY, entry.at().x());
                at.putInt(Y_KEY, entry.at().y());
                at.putInt(Z_KEY, entry.at().z());
                item.put(AT_KEY, at);
            }
            stuck.add(item);
        }
        tag.put(STUCK_KEY, stuck);
    }

    static Parts read(CompoundTag tag) {
        List<BoardState.Blocked> blocked = new ArrayList<>();
        ListTag blockedTag = tag.getList(BLOCKED_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < blockedTag.size(); index++) {
            CompoundTag item = blockedTag.getCompound(index);
            blocked.add(new BoardState.Blocked(
                    item.getString(ID_KEY),
                    items(item.getCompound(MISSING_KEY)),
                    items(item.getCompound(AVAILABLE_KEY)),
                    item.getLong(SINCE_KEY),
                    item.contains(BLOCK_KEY) ? blockOf(item.getCompound(BLOCK_KEY)) : null));
        }
        List<BoardState.Pause> pauses = new ArrayList<>();
        ListTag pausesTag = tag.getList(PAUSES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < pausesTag.size(); index++) {
            CompoundTag item = pausesTag.getCompound(index);
            pauses.add(new BoardState.Pause(
                    item.getUUID(SPIRIT_KEY),
                    BoardCase.valueOf(item.getString(CASE_KEY)),
                    item.getInt(CONSECUTIVE_KEY),
                    item.getLong(PAUSED_UNTIL_KEY),
                    item.getBoolean(DETACHED_KEY),
                    item.contains(TOKEN_KEY) ? item.getString(TOKEN_KEY) : null));
        }
        List<BoardState.Stuck> stuck = new ArrayList<>();
        ListTag stuckTag = tag.getList(STUCK_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < stuckTag.size(); index++) {
            CompoundTag item = stuckTag.getCompound(index);
            BlockCoord at = null;
            if (item.contains(AT_KEY)) {
                CompoundTag point = item.getCompound(AT_KEY);
                at = new BlockCoord(point.getInt(X_KEY), point.getInt(Y_KEY), point.getInt(Z_KEY));
            }
            stuck.add(new BoardState.Stuck(
                    item.getUUID(SPIRIT_KEY), at, item.getLong(SINCE_KEY), item.getBoolean(NO_PATH_KEY)));
        }
        return new Parts(blocked, pauses, stuck);
    }

    private static CompoundTag items(Map<String, Integer> values) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            tag.putInt(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    private static Map<String, Integer> items(CompoundTag tag) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            values.put(key, tag.getInt(key));
        }
        return Map.copyOf(values);
    }

    private static CompoundTag blockTag(GraphBlock block) {
        CompoundTag tag = new CompoundTag();
        tag.putString(BLOCK_NODE_KEY, block.node());
        tag.putString(BLOCK_TARGET_KEY, block.target());
        GraphBlock.Need need = block.need();
        if (need instanceof GraphBlock.Item item) {
            tag.putString(BLOCK_KIND_KEY, "item");
            tag.putString(BLOCK_ITEM_KEY, item.item());
            tag.putInt(BLOCK_COUNT_KEY, item.count());
        } else if (need instanceof GraphBlock.Tool tool) {
            tag.putString(BLOCK_KIND_KEY, "tool");
            tag.putString(BLOCK_TOOL_KEY, tool.kind());
            tag.putInt(BLOCK_RANK_KEY, tool.rank());
            tag.putString(BLOCK_SPIRIT_KEY, tool.spirit());
        } else if (need instanceof GraphBlock.Building building) {
            tag.putString(BLOCK_KIND_KEY, "building");
            tag.putString(BLOCK_BUILDING_KEY, building.id());
        } else if (need instanceof GraphBlock.Path path) {
            tag.putString(BLOCK_KIND_KEY, "path");
            tag.putString(BLOCK_POINT_KEY, path.point());
        } else if (need instanceof GraphBlock.Holder holder) {
            tag.putString(BLOCK_KIND_KEY, "holder");
            tag.putString(BLOCK_SPIRIT_KEY, holder.spirit());
            tag.putBoolean(BLOCK_STUCK_KEY, holder.stuck());
        } else if (need instanceof GraphBlock.World world) {
            tag.putString(BLOCK_KIND_KEY, "world");
            tag.putString(BLOCK_TAG_KEY, world.tag());
        } else if (need instanceof GraphBlock.Impossible impossible) {
            tag.putString(BLOCK_KIND_KEY, "impossible");
            tag.putString(BLOCK_NODE_KEY, impossible.node());
        } else {
            throw new IllegalStateException("Неизвестная нужда блокировки: " + need);
        }
        return tag;
    }

    private static GraphBlock blockOf(CompoundTag tag) {
        String node = tag.getString(BLOCK_NODE_KEY);
        String target = tag.getString(BLOCK_TARGET_KEY);
        GraphBlock.Need need = switch (tag.getString(BLOCK_KIND_KEY)) {
            case "item" -> new GraphBlock.Item(tag.getString(BLOCK_ITEM_KEY), tag.getInt(BLOCK_COUNT_KEY));
            case "tool" ->
                new GraphBlock.Tool(
                        tag.getString(BLOCK_TOOL_KEY), tag.getInt(BLOCK_RANK_KEY), tag.getString(BLOCK_SPIRIT_KEY));
            case "building" -> new GraphBlock.Building(tag.getString(BLOCK_BUILDING_KEY));
            case "path" -> new GraphBlock.Path(tag.getString(BLOCK_POINT_KEY));
            case "holder" -> new GraphBlock.Holder(tag.getString(BLOCK_SPIRIT_KEY), tag.getBoolean(BLOCK_STUCK_KEY));
            case "world" -> new GraphBlock.World(tag.getString(BLOCK_TAG_KEY));
            case "impossible" -> new GraphBlock.Impossible(tag.getString(BLOCK_NODE_KEY));
            default -> throw new IllegalStateException("Неизвестный вид блокировки: " + tag.getString(BLOCK_KIND_KEY));
        };
        return new GraphBlock(node, target, need);
    }
}
