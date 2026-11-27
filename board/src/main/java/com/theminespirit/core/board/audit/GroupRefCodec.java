package com.theminespirit.core.board.audit;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class GroupRefCodec {
    private static final String GROUP = "Group";
    private static final String REVISION = "GroupRevision";

    private GroupRefCodec() {}

    public static void put(Map<String, Object> map, @Nullable GroupRef ref) {
        Objects.requireNonNull(map, "Запись цели не может быть null: ссылку группы класть некуда");
        if (ref == null) {
            return;
        }
        map.put(GROUP, ref.id());
        map.put(REVISION, ref.revision());
    }

    public static @Nullable GroupRef decode(Map<String, Object> map) {
        Objects.requireNonNull(map, "Запись цели не может быть null: ссылку группы читать негде");
        Object rawId = map.get(GROUP);
        if (rawId == null) {
            return null;
        }
        if (!(rawId instanceof String id)) {
            throw new IllegalArgumentException("Запись Group не строка: ссылка на группу не читается");
        }
        if (!map.containsKey(REVISION)) {
            throw new IllegalArgumentException("В записи группы есть id, но нет ревизии: билет не сможет"
                    + " выбрать снимок работы, проверьте NBT доски");
        }
        Object rawRevision = map.get(REVISION);
        if (!(rawRevision instanceof Number number)) {
            throw new IllegalArgumentException("Запись GroupRevision не число: ревизия группы не читается");
        }
        return new GroupRef(id, number.intValue());
    }
}
