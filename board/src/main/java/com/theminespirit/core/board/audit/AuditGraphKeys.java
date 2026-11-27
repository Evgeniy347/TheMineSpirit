package com.theminespirit.core.board.audit;

import java.util.Locale;
import java.util.Objects;

public final class AuditGraphKeys {
    private AuditGraphKeys() {}

    public static String key(Group group) {
        Objects.requireNonNull(group, "Группа не может быть null: графовый ключ строится по ней");
        String kind = group.kind().name().toLowerCase(Locale.ROOT);
        String key = "audit:" + kind;
        if (!group.subject().isBlank()) {
            key += ":" + group.subject();
        }
        if (group.chunk() != null) {
            key += "@" + group.chunk().x() + "," + group.chunk().z();
        }
        return key;
    }

    public static boolean active(Group group) {
        return group.status() == Group.Status.PENDING || group.status() == Group.Status.ISSUED;
    }
}
