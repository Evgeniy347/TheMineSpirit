package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class BoardStateStore extends SavedData {
    public static final String NAME = "theminespirit_board";

    private static final String VERSION_KEY = "Version";
    private static final int VERSION = 2;
    private static final String ENTRIES_KEY = "Entries";
    private static final String LEDGER_KEY = "Ledger";
    private static final String BUILD_KEY = "Build";
    private static final String ID_KEY = "Id";
    private static final String CASE_KEY = "Case";
    private static final String PRIORITY_KEY = "Priority";
    private static final String CREATED_KEY = "CreatedAt";
    private static final String LEVEL_KEY = "Level";
    private static final String CAUSE_KEY = "Cause";
    private static final String DEPENDS_KEY = "DependsOn";
    private static final String REQUIRED_KEY = "RequiredItems";
    private static final String MODIFIERS_KEY = "Modifiers";
    private static final String EXECUTORS_KEY = "Executors";
    private static final String ASSIGNEE_KEY = "Assignee";
    private static final String STATUS_KEY = "Status";
    private static final String HOLDER_KEY = "Holder";
    private static final String FAILED_AT_KEY = "FailedAt";
    private static final String FAILURES_KEY = "Failures";

    private BoardState state = BoardState.empty();
    private String build = "";

    public BoardStateStore() {}

    public static SavedData.Factory<BoardStateStore> factory() {
        return new SavedData.Factory<>(BoardStateStore::new, BoardStateStore::load, null);
    }

    public static BoardStateStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static BoardStateStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BoardStateStore store = new BoardStateStore();
        ListTag entries = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        int version = tag.getInt(VERSION_KEY);
        if ((version < 1 || version > VERSION) && !entries.isEmpty()) {
            throw new IllegalStateException("Доска записана версией " + version + ", поддерживается до " + VERSION
                    + ": обновите мод, старое состояние доски не читается");
        }
        List<BoardState.Entry> restored = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            restored.add(readEntry(entries.getCompound(index)));
        }
        Set<String> ledger = new LinkedHashSet<>();
        ListTag marks = tag.getList(LEDGER_KEY, Tag.TAG_STRING);
        for (int index = 0; index < marks.size(); index++) {
            ledger.add(marks.getString(index));
        }
        BoardStateNbt.Parts parts =
                version >= 2 ? BoardStateNbt.read(tag) : new BoardStateNbt.Parts(List.of(), List.of(), List.of());
        store.state = new BoardState(restored, ledger, parts.blocked(), parts.pauses(), parts.stuck());
        store.build = tag.getString(BUILD_KEY);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, VERSION);
        ListTag entries = new ListTag();
        for (BoardState.Entry entry : state.entries()) {
            entries.add(writeEntry(entry));
        }
        tag.put(ENTRIES_KEY, entries);
        ListTag marks = new ListTag();
        for (String key : state.ledger()) {
            marks.add(StringTag.valueOf(key));
        }
        tag.put(LEDGER_KEY, marks);
        BoardStateNbt.write(tag, state);
        tag.putString(BUILD_KEY, build);
        return tag;
    }

    public String build() {
        return build;
    }

    public void markBuild(String current) {
        build = Objects.requireNonNull(current, "Сборка не может быть null: по ней сбрасывают старые правила");
        setDirty();
    }

    public BoardState state() {
        return state;
    }

    public void update(BoardState next) {
        Objects.requireNonNull(next, "Снимок доски не может быть null: сохранять нечего");
        if (!state.equals(next)) {
            state = next;
            setDirty();
        }
    }

    private static CompoundTag writeEntry(BoardState.Entry entry) {
        BoardTicket ticket = entry.ticket();
        CompoundTag tag = new CompoundTag();
        tag.putString(ID_KEY, ticket.id());
        tag.putString(CASE_KEY, ticket.boardCase().name());
        tag.putInt(PRIORITY_KEY, ticket.priority());
        tag.putLong(CREATED_KEY, ticket.createdAt());
        tag.putInt(LEVEL_KEY, ticket.level());
        tag.putString(CAUSE_KEY, ticket.cause());
        ListTag depends = new ListTag();
        for (String dependency : ticket.dependsOn()) {
            depends.add(StringTag.valueOf(dependency));
        }
        tag.put(DEPENDS_KEY, depends);
        CompoundTag required = new CompoundTag();
        for (Map.Entry<String, Integer> item : ticket.requiredItems().entrySet()) {
            required.putInt(item.getKey(), item.getValue());
        }
        tag.put(REQUIRED_KEY, required);
        ListTag modifiers = new ListTag();
        for (String modifier : ticket.modifiers()) {
            modifiers.add(StringTag.valueOf(modifier));
        }
        tag.put(MODIFIERS_KEY, modifiers);
        ListTag executors = new ListTag();
        for (UUID executor : ticket.executors()) {
            executors.add(StringTag.valueOf(executor.toString()));
        }
        tag.put(EXECUTORS_KEY, executors);
        ticket.assignee().ifPresent(assignee -> tag.putUUID(ASSIGNEE_KEY, assignee));
        tag.putString(STATUS_KEY, entry.status().name());
        UUID holder = entry.holder();
        if (holder != null) {
            tag.putUUID(HOLDER_KEY, holder);
        }
        Long failedAt = entry.failedAt();
        if (failedAt != null) {
            tag.putLong(FAILED_AT_KEY, failedAt);
        }
        tag.putInt(FAILURES_KEY, entry.failures());
        return tag;
    }

    private static BoardState.Entry readEntry(CompoundTag tag) {
        List<String> depends = new ArrayList<>();
        ListTag dependsTag = tag.getList(DEPENDS_KEY, Tag.TAG_STRING);
        for (int index = 0; index < dependsTag.size(); index++) {
            depends.add(dependsTag.getString(index));
        }
        Map<String, Integer> required = new LinkedHashMap<>();
        CompoundTag requiredTag = tag.getCompound(REQUIRED_KEY);
        for (String key : requiredTag.getAllKeys()) {
            required.put(key, requiredTag.getInt(key));
        }
        List<String> modifiers = new ArrayList<>();
        ListTag modifiersTag = tag.getList(MODIFIERS_KEY, Tag.TAG_STRING);
        for (int index = 0; index < modifiersTag.size(); index++) {
            modifiers.add(modifiersTag.getString(index));
        }
        List<UUID> executors = new ArrayList<>();
        ListTag executorsTag = tag.getList(EXECUTORS_KEY, Tag.TAG_STRING);
        for (int index = 0; index < executorsTag.size(); index++) {
            executors.add(UUID.fromString(executorsTag.getString(index)));
        }
        BoardTicket ticket = new BoardTicket(
                tag.getString(ID_KEY),
                boardCase(tag.getString(CASE_KEY)),
                optionalUuid(tag, ASSIGNEE_KEY),
                tag.getInt(PRIORITY_KEY),
                tag.getLong(CREATED_KEY),
                tag.getInt(LEVEL_KEY),
                tag.getString(CAUSE_KEY),
                depends,
                required,
                modifiers,
                executors);
        Long failedAt = tag.contains(FAILED_AT_KEY) ? tag.getLong(FAILED_AT_KEY) : null;
        return new BoardState.Entry(
                ticket,
                boardStatus(tag.getString(STATUS_KEY)),
                tag.contains(HOLDER_KEY) ? tag.getUUID(HOLDER_KEY) : null,
                failedAt,
                tag.getInt(FAILURES_KEY));
    }

    private static BoardCase boardCase(String name) {
        try {
            return BoardCase.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException("Кейс доски '" + name + "' неизвестен этой версии мода: "
                    + "состояние доски не читается, проверьте сборку");
        }
    }

    private static BoardStatus boardStatus(String name) {
        try {
            return BoardStatus.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException("Статус доски '" + name + "' неизвестен этой версии мода: "
                    + "состояние доски не читается, проверьте сборку");
        }
    }

    private static Optional<UUID> optionalUuid(CompoundTag tag, String key) {
        return tag.contains(key) ? Optional.of(tag.getUUID(key)) : Optional.empty();
    }
}
