package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ExpeditionMinePlan;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.intellect.ExpeditionController;
import com.theminespirit.neoforge.intellect.ExpeditionKit;
import com.theminespirit.neoforge.intellect.ExpeditionMember;
import com.theminespirit.neoforge.intellect.SpiritExpeditionMember;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class GraphExpeditionPost {
    private static final String MINE = "mine_vein";

    private GraphExpeditionPost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        List<SpiritEntity> members = new ArrayList<>();
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved() && !ActivityLaunch.stuck(post, spirit)) {
                members.add(spirit);
            }
        }
        SettlementKey key = SettlementGroups.keyOf(
                Optional.of(post.scan.home()),
                members.isEmpty() ? null : members.get(0).getUUID());
        ExpeditionController controller =
                CoordinatorRegistry.get(post.level, key).intellect().expeditions();
        List<UUID> team = team(
                post.scan.home(),
                members,
                controller.active().map(Expedition::team).orElse(null));
        Optional<UUID> leader = team.stream().findFirst();
        BlockCoord bottom =
                MineDescentBottom.standOf(post.scan.stage().mineEntrance()).orElse(null);
        BoardStatus status = post.service.board().statusOf(ticket.id()).orElse(null);
        if (status == null || status == BoardStatus.PENDING) {
            GraphActivityPost.post(
                    post,
                    new GraphActivityPost.Post(
                            ticket.id(),
                            ticket.task().boardCase(),
                            new GraphActivityPost.Crew(leader, team),
                            GraphActivityPost.priority(post),
                            MINE,
                            plan(bottom),
                            null));
        }
        List<UUID> crew = GraphActivityPost.ticket(post, ticket.id())
                .map(BoardTicket::executors)
                .orElse(team);
        String wait = waitReason(team, post, controller, bottom);
        if (status == BoardStatus.ASSIGNED) {
            post.service.activities().hold(ticket.id(), null);
        } else {
            post.service.activities().hold(ticket.id(), wait);
        }
        if (wait == null
                && bottom != null
                && controller.active().isEmpty()
                && !controller.dispatchedRecently(post.now)
                && team.size() >= IntellectTuning.loadDefault().teamMin()) {
            controller.offer(new Expedition(team, point(bottom), post.now));
        }
        Map<UUID, SpiritEntity> byId = new HashMap<>();
        for (SpiritEntity spirit : post.spirits) {
            byId.put(spirit.getUUID(), spirit);
        }
        controller.update(
                post.spirits.stream().map(SpiritExpeditionMember::of).toList(),
                post.stock,
                post.now,
                (id, script, task, tick) -> {
                    SpiritEntity spirit = byId.get(id);
                    return spirit != null && launch(post, ticket.id(), crew, spirit, task);
                });
        if (status != BoardStatus.ASSIGNED) {
            String refused = controller.refusal().orElse(null);
            if (refused != null) {
                post.service.activities().hold(ticket.id(), refused);
            }
        }
        return true;
    }

    private static boolean launch(GraphPost post, String id, List<UUID> crew, SpiritEntity spirit, TaskNode task) {
        if (spirit.isRemoved() || ActivityLaunch.stuck(post, spirit)) {
            return false;
        }
        Optional<UUID> leader = crew.isEmpty() ? Optional.empty() : Optional.of(crew.get(0));
        if (leader.filter(spirit.getUUID()::equals).isPresent()) {
            post.service.activities().hold(id, null);
            return post.service.activities().start(id, spirit, task, post.now);
        }
        return post.service.activities().startCrew(id, spirit, task);
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

    private static @Nullable String waitReason(
            List<UUID> team, GraphPost post, ExpeditionController controller, @Nullable BlockCoord bottom) {
        int min = IntellectTuning.loadDefault().teamMin();
        if (team.size() < min) {
            return "нет команды: нужно " + min + " духа, рядом " + team.size();
        }
        if (bottom == null) {
            return "низ шахты не найден: спуск не построен";
        }
        List<ExpeditionMember> members = new ArrayList<>();
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved() && team.contains(spirit.getUUID())) {
                members.add(SpiritExpeditionMember.of(spirit));
            }
        }
        List<String> missing = ExpeditionKit.missing(members, post.stock);
        if (!missing.isEmpty()) {
            return "перед спуском в шахту нужен набор: " + String.join(", ", missing);
        }
        if (controller.dispatchedRecently(post.now)) {
            return "поход недавно вернулся: ждёт нового повода";
        }
        return null;
    }

    private static TaskNode plan(@Nullable BlockCoord bottom) {
        return bottom == null
                ? new ScriptCall(ExpeditionMinePlan.RETURN_HOME, Map.of())
                : ExpeditionMinePlan.plan(bottom);
    }

    private static Point point(BlockCoord bottom) {
        return new Point(bottom.x() + 0.5, bottom.y(), bottom.z() + 0.5);
    }
}
