package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.town.Placement;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class AuditGroupLookup {
    private AuditGroupLookup() {}

    public static @Nullable Group find(ServerLevel level, String scheme, int x, int z) {
        GroupRegistry registry = TownAuditWatch.peekGroups(level);
        if (registry == null) {
            return null;
        }
        return planId(level, scheme, x, z)
                .map(id -> {
                    Group building = registry.group(Group.Kind.BUILDING, id, x, z);
                    return building != null ? building : registry.group(Group.Kind.MINE, id, x, z);
                })
                .orElse(null);
    }

    public static List<Group> findAll(ServerLevel level, String scheme, int x, int z) {
        GroupRegistry registry = TownAuditWatch.peekGroups(level);
        if (registry == null) {
            return List.of();
        }
        return planId(level, scheme, x, z)
                .map(id -> {
                    List<Group> buildings = registry.groups(Group.Kind.BUILDING, id);
                    return buildings.isEmpty() ? registry.groups(Group.Kind.MINE, id) : buildings;
                })
                .orElseGet(List::of);
    }

    public static String ticketOf(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return "";
        }
        return BoardRegistry.get(level).board().assignedTo(spirit.getUUID()).stream()
                .map(BoardTicket::id)
                .findFirst()
                .orElse("");
    }

    private static Optional<String> planId(ServerLevel level, String scheme, int x, int z) {
        return SettlementPlotStore.get(level).town().stream()
                .flatMap(plan -> plan.placements().stream())
                .filter(place -> place.x() == x && place.z() == z)
                .filter(place -> StageCatalog.scheme(place.id(), place.stage())
                        .filter(scheme::equals)
                        .isPresent())
                .map(Placement::id)
                .findFirst();
    }
}
