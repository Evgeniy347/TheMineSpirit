package com.theminespirit.core.intellect;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExpeditionDispatch {
    public static final String MINE = "mine_vein";
    public static final String FOLLOW = "follow";
    public static final String RETURN_HOME = "return_home";
    private static final int PARTNER_RADIUS = 3;
    private static final Map<String, Object> MINE_PARAMS = Map.of("ore", "any", "count", 8, "radius", 16);

    private ExpeditionDispatch() {}

    public record Order(UUID spirit, String script, TaskNode task) {}

    public record Plan(UUID leader, List<Order> orders) {}

    public static Plan plan(List<UUID> present, UUID preferredLeader) {
        if (present == null || preferredLeader == null) {
            throw new IllegalArgumentException(
                    "Поход без команды или ведущего не планируется: ведущий копает, напарники идут за ним");
        }
        if (present.isEmpty()) {
            return new Plan(preferredLeader, List.of());
        }
        UUID leader = present.contains(preferredLeader) ? preferredLeader : present.get(0);
        List<Order> orders = new ArrayList<>();
        for (UUID spirit : present) {
            if (spirit.equals(leader)) {
                orders.add(new Order(
                        spirit,
                        MINE,
                        new SequenceNode(
                                List.of(new ScriptCall(MINE, MINE_PARAMS), new ScriptCall(RETURN_HOME, Map.of())))));
            } else {
                orders.add(new Order(
                        spirit,
                        FOLLOW,
                        new ScriptCall(FOLLOW, Map.of("target", leader.toString(), "radius", PARTNER_RADIUS))));
            }
        }
        return new Plan(leader, List.copyOf(orders));
    }
}
