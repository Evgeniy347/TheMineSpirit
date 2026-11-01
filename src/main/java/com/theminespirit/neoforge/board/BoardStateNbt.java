package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardState;
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
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";

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
                    item.getLong(SINCE_KEY)));
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
            stuck.add(new BoardState.Stuck(item.getUUID(SPIRIT_KEY), at, item.getLong(SINCE_KEY)));
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
}
