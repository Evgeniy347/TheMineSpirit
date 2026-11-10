package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ErrorText;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.TicketTargetCodec;
import com.theminespirit.core.board.TicketTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

final class BoardTargetsNbt {
    static final String KEY = "Targets";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TARGET_ID_KEY = "Id";
    private static final String TARGET_DATA_KEY = "Data";

    private BoardTargetsNbt() {}

    static void restore(BoardStateStore store, Board board, Map<String, TicketTarget> targets, BoardSources sources) {
        sources.restoreGraphFailures(store.failures());
        targets.putAll(store.targets());
        Set<String> alive = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            alive.add(ticket.id());
        }
        targets.keySet().retainAll(alive);
        List<String> cancelled = TicketTargets.dropBroken(board, store.brokenTargets());
        if (!cancelled.isEmpty()) {
            LOGGER.warn(
                    "[board] при загрузке снято {} заданий с нечитаемой целью {}: без цели задание исполнялось бы "
                            + "у дома; источники графа поставят его заново",
                    cancelled.size(),
                    cancelled);
        }
    }

    static void persist(
            BoardStateStore store, BoardState snapshot, Map<String, TicketTarget> targets, BoardSources sources) {
        store.update(snapshot);
        store.updateTargets(targets);
        store.updateFailures(sources.graphFailureSnapshots());
    }

    static ListTag write(Map<String, TicketTarget> targets) {
        ListTag list = new ListTag();
        for (Map.Entry<String, TicketTarget> entry : targets.entrySet()) {
            Optional<Map<String, Object>> encoded = TicketTargetCodec.encode(entry.getValue());
            if (encoded.isEmpty()) {
                continue;
            }
            CompoundTag item = new CompoundTag();
            item.putString(TARGET_ID_KEY, entry.getKey());
            item.put(TARGET_DATA_KEY, mapTag(encoded.orElseThrow()));
            list.add(item);
        }
        return list;
    }

    static Read read(CompoundTag tag) {
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        List<TicketTargets.Broken> broken = new ArrayList<>();
        ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag item = list.getCompound(index);
            String id = item.getString(TARGET_ID_KEY);
            try {
                raw.put(id, map(item.getCompound(TARGET_DATA_KEY)));
            } catch (RuntimeException unreadable) {
                broken.add(new TicketTargets.Broken(id, ErrorText.of(unreadable)));
            }
        }
        TicketTargets decoded = TicketTargets.decode(raw);
        broken.addAll(decoded.broken());
        for (TicketTargets.Broken target : broken) {
            LOGGER.warn(
                    "[board] цель задания {} не читается: {}; задание будет снято, а не исполнено у дома",
                    target.id(),
                    target.message());
        }
        return new Read(
                decoded.targets(), broken.stream().map(TicketTargets.Broken::id).toList());
    }

    record Read(Map<String, TicketTarget> targets, List<String> broken) {}

    private static CompoundTag mapTag(Map<String, Object> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach((key, value) -> {
            if (value instanceof String text) {
                tag.putString(key, text);
            } else if (value instanceof Number number) {
                tag.putInt(key, number.intValue());
            } else if (value instanceof List<?> list) {
                tag.put(key, listTag(key, list));
            } else {
                throw new IllegalStateException("Цель доски: поле " + key + " типа "
                        + value.getClass().getSimpleName() + " не сохраняется, проверьте TicketTargetCodec");
            }
        });
        return tag;
    }

    private static ListTag listTag(String key, List<?> values) {
        ListTag list = new ListTag();
        for (Object value : values) {
            if (value instanceof String text) {
                list.add(StringTag.valueOf(text));
            } else if (value instanceof Number number) {
                list.add(IntTag.valueOf(number.intValue()));
            } else {
                throw new IllegalStateException(
                        "Цель доски: поле " + key + " содержит " + value + " — списки хранят строки или числа");
            }
        }
        return list;
    }

    private static Map<String, Object> map(CompoundTag tag) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value == null) {
                continue;
            }
            switch (value.getId()) {
                case Tag.TAG_STRING -> values.put(key, tag.getString(key));
                case Tag.TAG_INT -> values.put(key, tag.getInt(key));
                case Tag.TAG_LIST -> values.put(key, list((ListTag) value));
                default ->
                    throw new IllegalStateException(
                            "Цель доски: поле " + key + " тега " + value.getId() + " не читается, проверьте NBT доски");
            }
        }
        return values;
    }

    private static List<Object> list(ListTag tag) {
        List<Object> values = new ArrayList<>(tag.size());
        for (int index = 0; index < tag.size(); index++) {
            Tag element = tag.get(index);
            switch (element.getId()) {
                case Tag.TAG_STRING -> values.add(tag.getString(index));
                case Tag.TAG_INT -> values.add(tag.getInt(index));
                default ->
                    throw new IllegalStateException(
                            "Цель доски: список содержит тег " + element.getId() + ", проверьте NBT доски");
            }
        }
        return List.copyOf(values);
    }
}
