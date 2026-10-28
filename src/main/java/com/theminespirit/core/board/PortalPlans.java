package com.theminespirit.core.board;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CastObsidianParams;
import com.theminespirit.core.script.LightPortalParams;
import com.theminespirit.core.script.PortalScripts;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PortalPlans {
    public static final String OBSIDIAN = "obsidian";
    public static final String PORTAL_LIT = "portal_lit";
    private static final String WATER_BUCKET = "minecraft:water_bucket";
    private static final String FLINT_AND_STEEL = "minecraft:flint_and_steel";

    private PortalPlans() {}

    public static Optional<TaskNode> special(TicketTarget target, String item, int batch) {
        Objects.requireNonNull(target, "Цель задания не может быть null: от неё строится план портала");
        if (PORTAL_LIT.equals(target.schemeFile())) {
            return Optional.of(lightPortal(target));
        }
        if (OBSIDIAN.equals(item)) {
            return Optional.of(castObsidian(target, batch));
        }
        return Optional.empty();
    }

    static TaskNode castObsidian(TicketTarget target, int count) {
        return new SequenceNode(List.of(
                TicketPlans.gotoTarget(target),
                TicketPlans.takeFrom(WATER_BUCKET, count),
                new ScriptCall(
                        PortalScripts.CAST_OBSIDIAN,
                        Map.of("count", count, "radius", CastObsidianParams.RADIUS_DEFAULT))));
    }

    static TaskNode lightPortal(TicketTarget target) {
        return new SequenceNode(List.of(
                TicketPlans.gotoTarget(target),
                TicketPlans.takeFrom(FLINT_AND_STEEL, 1),
                new ScriptCall(PortalScripts.LIGHT_PORTAL, Map.of("radius", LightPortalParams.RADIUS_DEFAULT))));
    }
}
