package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.audit.Group;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class AuditGroupsStore {
    private static volatile List<Group> CURRENT = List.of();

    private AuditGroupsStore() {}

    public static void publish(ServerLevel level, List<Group> groups) {
        Objects.requireNonNull(groups, "Группы аудита не могут быть null: пустой список — List.of()");
        if (level.dimension().equals(Level.OVERWORLD)) {
            CURRENT = List.copyOf(groups);
        }
    }

    public static void clear(ServerLevel level) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            CURRENT = List.of();
        }
    }

    public static List<Group> get() {
        return CURRENT;
    }
}
