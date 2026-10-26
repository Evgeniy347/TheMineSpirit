package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ChopTargetParams;
import java.util.Map;
import java.util.Objects;

public final class OrchardPlans {
    public static final String PREFIX = "orchard:";

    private OrchardPlans() {}

    public static TaskNode harvest(TicketTarget target) {
        Objects.requireNonNull(target, "Цель рубки сада не может быть null: в ней ствол зрелого дерева");
        BlockCoord trunk = target.target();
        return new ScriptCall(
                "chop_target",
                Map.of(
                        "x",
                        trunk.x(),
                        "y",
                        trunk.y(),
                        "z",
                        trunk.z(),
                        "replant",
                        true,
                        ChopTargetParams.ORCHARD,
                        true));
    }

    public static String id(BlockCoord spot) {
        Objects.requireNonNull(spot, "Место дерева сада не может быть null: задание сада — на место из плана");
        return PREFIX + spot.x() + "," + spot.z();
    }

    public static boolean mature(int trunkLogs, WoodRules rules) {
        return trunkLogs >= rules.orchardMatureLogs();
    }
}
