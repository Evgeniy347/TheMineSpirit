package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

final class More3Post {
    private static final int DANGER_RADIUS = 8;
    private static final int CONTAINER_RADIUS = 8;
    private static final Set<String> NODES = Set.of(
            "secure_danger",
            "fence_danger",
            "recover_death_drop",
            "clear_trash",
            "assign_chest_purpose",
            "move_work",
            "stay_near_home");

    private More3Post() {}

    static boolean handles(String node) {
        return NODES.contains(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        return switch (ticket.node()) {
            case "secure_danger" -> avoidDanger(post);
            case "fence_danger" -> fenceDanger(post, ticket);
            case "recover_death_drop" -> recoverDrops(post, ticket);
            case "clear_trash" ->
                ActivityLaunch.launch(post, BoardCase.ACTIVITY_UNLOAD, "unload", new ScriptCall("unload", unload()));
            case "assign_chest_purpose" ->
                ActivityLaunch.launch(
                        post, BoardCase.ACTIVITY_UNLOAD, "sort_chest", new ScriptCall("sort_chest", sort()));
            case "move_work", "stay_near_home" ->
                ActivityLaunch.launch(post, BoardCase.ACTIVITY_MOVE, "goto", gotoPoint(post.scan.home()));
            default -> false;
        };
    }

    private static boolean fenceDanger(GraphPost post, GraphTickets.Ticket ticket) {
        for (More3Store.Danger danger : More3Store.get(post.level).recentDangers(post.now)) {
            BlockCoord at = danger.position();
            String id = "more3_fence:" + at.x() + "," + at.y() + "," + at.z();
            if (post.done(id) || post.open(id)) {
                return true;
            }
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.BUILD_FENCE,
                            Optional.empty(),
                            ticket.task().priority(),
                            ticket.id(),
                            post.items(ticket.task())),
                    TicketTarget.at(at));
            return true;
        }
        return false;
    }

    private static boolean avoidDanger(GraphPost post) {
        BlockCoord home = post.scan.home();
        for (More3Store.Danger danger : More3Store.get(post.level).recentDangers(post.now)) {
            for (SpiritEntity spirit : post.spirits) {
                if (spirit.isRemoved()) {
                    continue;
                }
                BlockPos feet = spirit.blockPosition();
                if (!near(danger.position(), new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), DANGER_RADIUS)) {
                    continue;
                }
                if (ActivityLaunch.launchNow(post, spirit, BoardCase.ACTIVITY_MOVE, "goto", gotoPoint(home))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean recoverDrops(GraphPost post, GraphTickets.Ticket ticket) {
        boolean posted = false;
        for (More3Store.DeathDrop drop : More3Store.get(post.level).activeDrops(post.now)) {
            BlockCoord at = drop.position();
            String id = "more3_drop:" + at.x() + "," + at.y() + "," + at.z();
            if (post.done(id) || post.open(id)) {
                posted = true;
                continue;
            }
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.COLLECT_DROPS,
                            Optional.empty(),
                            ticket.task().priority(),
                            ticket.id(),
                            Map.of()),
                    TicketTarget.at(at));
            posted = true;
        }
        return posted;
    }

    private static TaskNode gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static Map<String, Object> unload() {
        return Map.of("container", "nearest", "radius", CONTAINER_RADIUS, "force", false);
    }

    private static Map<String, Object> sort() {
        return Map.of("container", "nearest", "radius", CONTAINER_RADIUS);
    }

    static boolean near(BlockCoord one, BlockCoord two, int radius) {
        int dx = one.x() - two.x();
        int dy = one.y() - two.y();
        int dz = one.z() - two.z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
