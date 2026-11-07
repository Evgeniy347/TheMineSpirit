package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.intellect.ExpeditionController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class GraphExpeditionPost {
    private GraphExpeditionPost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        List<SpiritEntity> members = new ArrayList<>();
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved()) {
                members.add(spirit);
            }
        }
        SettlementKey key = SettlementGroups.keyOf(
                Optional.of(post.scan.home()),
                members.isEmpty() ? null : members.get(0).getUUID());
        ExpeditionController controller =
                CoordinatorRegistry.get(post.level, key).intellect().expeditions();
        List<UUID> team = team(post.scan.home(), members, nearestTeam(controller));
        Optional<UUID> leader = team.stream().findFirst();
        boolean known = known(post, ticket.id());
        if (!known) {
            GraphActivityPost.post(
                    post,
                    new GraphActivityPost.Post(
                            ticket.id(),
                            ticket.task().boardCase(),
                            new GraphActivityPost.Crew(leader, team),
                            GraphActivityPost.priority(post),
                            "mine_vein",
                            ExpeditionController.missionPlan(),
                            null));
        }
        List<UUID> crew = known
                ? GraphActivityPost.ticket(post, ticket.id())
                        .map(BoardTicket::executors)
                        .orElse(team)
                : team;
        String wait = waitReason(team, post, controller);
        BoardStatus status = post.service.board().statusOf(ticket.id()).orElse(null);
        if (status == BoardStatus.ASSIGNED) {
            post.service.activities().hold(ticket.id(), null);
        } else {
            post.service.activities().hold(ticket.id(), wait);
        }
        if (wait == null
                && controller.active().isEmpty()
                && !controller.dispatchedRecently(post.now)
                && team.size() >= IntellectTuning.loadDefault().teamMin()) {
            controller.offer(new Expedition(team, mine(post.scan.home()), post.now));
        }
        controller.update(
                post.level,
                post.spirits,
                post.stock,
                post.now,
                (spirit, script, task, tick) -> launch(post, ticket.id(), crew, spirit, task));
        if (status != BoardStatus.ASSIGNED) {
            String refused = controller.refusal().orElse(null);
            if (refused != null) {
                post.service.activities().hold(ticket.id(), refused);
            }
        }
        return true;
    }

    private static boolean launch(GraphPost post, String id, List<UUID> crew, SpiritEntity spirit, TaskNode task) {
        if (spirit.isRemoved()) {
            return false;
        }
        Optional<UUID> leader = crew.isEmpty() ? Optional.empty() : Optional.of(crew.get(0));
        if (leader.filter(spirit.getUUID()::equals).isPresent()) {
            post.service.activities().hold(id, null);
            if (!post.service.activities().start(id, spirit, task, post.now)) {
                return false;
            }
            return true;
        }
        return post.service.activities().startCrew(id, spirit, task);
    }

    private static boolean known(GraphPost post, String id) {
        return GraphActivityPost.ticket(post, id).isPresent()
                && post.service.board().statusOf(id).orElse(null) != BoardStatus.DONE
                && post.service.board().statusOf(id).orElse(null) != BoardStatus.FAILED
                && post.service.board().statusOf(id).orElse(null) != BoardStatus.CANCELLED;
    }

    private static @Nullable List<UUID> nearestTeam(ExpeditionController controller) {
        return controller.active().map(Expedition::team).orElse(null);
    }

    private static List<UUID> team(BlockCoord home, List<SpiritEntity> members, @Nullable List<UUID> active) {
        if (active != null && !active.isEmpty()) {
            return active;
        }
        return members.stream()
                .sorted(Comparator.comparingDouble(
                                (SpiritEntity spirit) -> spirit.distanceToSqr(home.x() + 0.5, home.y(), home.z() + 0.5))
                        .thenComparing(spirit -> spirit.getUUID().toString()))
                .limit(IntellectTuning.loadDefault().teamMax())
                .map(SpiritEntity::getUUID)
                .toList();
    }

    private static @Nullable String waitReason(List<UUID> team, GraphPost post, ExpeditionController controller) {
        int min = IntellectTuning.loadDefault().teamMin();
        if (team.size() < min) {
            return "нет команды: нужно " + min + " духа, рядом " + team.size();
        }
        List<SpiritEntity> members = new ArrayList<>();
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved() && team.contains(spirit.getUUID())) {
                members.add(spirit);
            }
        }
        List<String> missing = ExpeditionController.missingKit(members, post.stock);
        if (!missing.isEmpty()) {
            return "перед спуском в шахту нужен набор: " + String.join(", ", missing);
        }
        if (controller.active().isPresent()) {
            return "поход ждёт ответа игрока: команда " + team.size() + " духа";
        }
        if (controller.dispatchedRecently(post.now)) {
            return "поход недавно вернулся: ждёт нового повода";
        }
        return null;
    }

    private static Point mine(BlockCoord home) {
        return new Point(home.x() + 0.5, home.y() - 10, home.z() + 0.5);
    }
}
