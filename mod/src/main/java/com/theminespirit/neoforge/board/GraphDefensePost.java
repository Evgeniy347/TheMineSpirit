package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.defense.DefenderOrder;
import com.theminespirit.core.intellect.defense.DefensePlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.SpiritSnapshotReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class GraphDefensePost {
    private GraphDefensePost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        List<SpiritSnapshot> snapshots = new ArrayList<>();
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved()) {
                snapshots.add(SpiritSnapshotReader.read(spirit, post.now));
            }
        }
        BlockCoord home = post.scan.home();
        CoordinatorState state = new CoordinatorState(
                Optional.of(new Point(home.x() + 0.5, home.y(), home.z() + 0.5)),
                CoordinatorTuning.DEFENSE_RADIUS,
                snapshots,
                post.now);
        DefensePlan plan = BoardRegistry.defense(post.level).plan(post.level, post.spirits, state);
        boolean posted = false;
        for (DefenderOrder order : plan.orders()) {
            if (order.target().isEmpty()) {
                continue;
            }
            SpiritEntity defender = defender(post, order.defender());
            if (defender == null) {
                continue;
            }
            String id = ticket.id() + ":" + defender.getUUID();
            if (post.open(id) || post.done(id)) {
                posted = true;
                continue;
            }
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.ACTIVITY_DEFEND,
                            Optional.of(defender.getUUID()),
                            priority(ticket),
                            ticket.id(),
                            Map.of()),
                    TicketTarget.at(position(order.target().get()))
                            .withPlan(new ScriptCall(
                                    "defend",
                                    Map.of(
                                            "target",
                                            target(post, ticket, order.target().get())))));
            posted = true;
        }
        if (!posted) {
            posted = fallback(post, ticket);
        }
        return posted;
    }

    private static boolean fallback(GraphPost post, GraphTickets.Ticket ticket) {
        GraphFacts.Threat threat = post.facts.threats().stream().findFirst().orElse(null);
        SpiritEntity defender = null;
        if (threat != null) {
            for (SpiritEntity spirit : post.spirits) {
                if (spirit.isRemoved()) {
                    continue;
                }
                if (defender == null
                        || spirit.distanceToSqr(
                                        threat.position().x(),
                                        threat.position().y(),
                                        threat.position().z())
                                < defender.distanceToSqr(
                                        threat.position().x(),
                                        threat.position().y(),
                                        threat.position().z())) {
                    defender = spirit;
                }
            }
        }
        if (defender == null) {
            return false;
        }
        String id = ticket.id() + ":" + defender.getUUID();
        if (post.open(id) || post.done(id)) {
            return true;
        }
        BlockCoord target = threat == null
                ? post.scan.home()
                : new BlockCoord(
                        threat.position().x(),
                        threat.position().y(),
                        threat.position().z());
        post.submit(
                post.ticket(
                        id,
                        BoardCase.ACTIVITY_DEFEND,
                        Optional.of(defender.getUUID()),
                        priority(ticket),
                        ticket.id(),
                        Map.of()),
                TicketTarget.at(target).withPlan(new ScriptCall("defend", Map.of("target", "hostile"))));
        return true;
    }

    static int priority(GraphTickets.Ticket ticket) {
        return Math.min(100, Math.max(1, ticket.task().priority()));
    }

    private static SpiritEntity defender(GraphPost post, UUID id) {
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved() && spirit.getUUID().equals(id)) {
                return spirit;
            }
        }
        return null;
    }

    private static String target(GraphPost post, GraphTickets.Ticket ticket, EnemyFact enemy) {
        if (!ticket.node().contains("protect_spirit")) {
            return "hostile";
        }
        return post.facts.threats().stream()
                .filter(threat -> threat.id().equals(enemy.id()))
                .map(GraphFacts.Threat::target)
                .filter(Objects::nonNull)
                .map(target -> "spirit:" + target)
                .findFirst()
                .orElse("hostile");
    }

    private static BlockCoord position(EnemyFact enemy) {
        return new BlockCoord(
                (int) Math.floor(enemy.pos().x()), (int) Math.floor(enemy.pos().y()), (int)
                        Math.floor(enemy.pos().z()));
    }
}
