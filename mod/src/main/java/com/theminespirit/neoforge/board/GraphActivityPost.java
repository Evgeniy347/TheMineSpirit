package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.ActivityTickets;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class GraphActivityPost {
    record Crew(Optional<UUID> leader, List<UUID> team) {}

    record Post(
            String id,
            BoardCase activity,
            Crew crew,
            int priority,
            String script,
            TaskNode task,
            @Nullable String hold,
            Map<String, Integer> requiredItems) {}

    private GraphActivityPost() {}

    static boolean request(GraphPost post, String key, int priority, BoardCase activity, String script, TaskNode task) {
        return post(
                post,
                new Post(key, activity, new Crew(Optional.empty(), List.of()), priority, script, task, null, Map.of()));
    }

    static boolean launch(GraphPost post, SpiritEntity spirit, BoardCase activity, ActivityPlans.Plan plan) {
        return post(
                post,
                new Post(
                        id(post, Optional.of(spirit.getUUID())),
                        activity,
                        new Crew(Optional.of(spirit.getUUID()), List.of()),
                        priority(post),
                        plan.script(),
                        plan.task(),
                        null,
                        Map.of()));
    }

    static boolean launchWithRequirements(
            GraphPost post,
            SpiritEntity spirit,
            BoardCase activity,
            ActivityPlans.Plan plan,
            Map<String, Integer> requiredItems) {
        return post(
                post,
                new Post(
                        id(post, Optional.of(spirit.getUUID())),
                        activity,
                        new Crew(Optional.of(spirit.getUUID()), List.of()),
                        priority(post),
                        plan.script(),
                        plan.task(),
                        null,
                        requiredItems));
    }

    static boolean launchNow(GraphPost post, SpiritEntity spirit, BoardCase activity, ActivityPlans.Plan plan) {
        String id = id(post, Optional.of(spirit.getUUID()));
        if (!post(
                post,
                new Post(
                        id,
                        activity,
                        new Crew(Optional.of(spirit.getUUID()), List.of()),
                        priority(post),
                        plan.script(),
                        plan.task(),
                        null,
                        Map.of()))) {
            return false;
        }
        return post.service.activities().start(id, spirit, plan.task(), post.now);
    }

    static boolean post(GraphPost post, Post request) {
        String cause = post.current == null ? "activity:" + request.script() : post.current.id();
        if (post.current != null
                && BoardActivities.open(post.service.board(), cause)
                && post.service.board().find(request.id()).isEmpty()) {
            return true;
        }
        int planned = post.current == null ? 0 : post.current.task().ticks();
        String target = BoardActivities.targetOf(request.task());
        ActivityTickets.Request ticket = new ActivityTickets.Request(
                new ActivityTickets.Identity(request.id(), request.activity(), cause),
                new ActivityTickets.Crew(request.crew().leader(), request.crew().team()),
                request.priority(),
                post.now,
                new ActivityTickets.Plan(request.script(), target == null ? "" : target, planned, request.task()),
                request.requiredItems());
        return post.service.activities().post(ticket, point(post, request.task()), request.hold());
    }

    static String id(GraphPost post, Optional<UUID> leader) {
        String base = post.current == null ? "activity:" + post.now : post.current.id();
        return leader.map(owner -> base + ":" + owner).orElse(base);
    }

    static int priority(GraphPost post) {
        return post.current == null ? 1 : post.current.task().priority();
    }

    static Optional<BoardTicket> ticket(GraphPost post, String id) {
        return post.service.activities().ticket(id);
    }

    private static BlockCoord point(GraphPost post, TaskNode task) {
        String label = BoardActivities.targetOf(task);
        if (label != null) {
            String[] parts = label.split(",", -1);
            if (parts.length == 3) {
                try {
                    return new BlockCoord(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()));
                } catch (NumberFormatException notPoint) {
                    return post.scan.home();
                }
            }
        }
        return post.scan.home();
    }
}
