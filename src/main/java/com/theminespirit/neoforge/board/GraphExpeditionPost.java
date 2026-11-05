package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.intellect.ExpeditionController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class GraphExpeditionPost {
    private GraphExpeditionPost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        List<SpiritEntity> members = new ArrayList<>();
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved()) {
                members.add(spirit);
            }
        }
        if (members.size() < IntellectTuning.loadDefault().teamMin()) {
            return false;
        }
        SettlementKey key = SettlementGroups.keyOf(
                Optional.of(post.scan.home()), members.get(0).getUUID());
        ExpeditionController controller =
                CoordinatorRegistry.get(post.level, key).intellect().expeditions();
        ExpeditionController.Launcher launcher = (spirit, script, task, now) ->
                post.launchActivity(spirit, BoardCase.ACTIVITY_EXPEDITION, new ActivityPlans.Plan(script, task));
        if (controller.active().isEmpty() && !controller.dispatchedRecently(post.now)) {
            controller.offer(expedition(post.scan.home(), members, post.now));
        }
        controller.update(post.level, post.spirits, post.now, launcher);
        return true;
    }

    private static Expedition expedition(BlockCoord home, List<SpiritEntity> members, long now) {
        List<UUID> team = members.stream()
                .sorted(Comparator.comparingDouble(
                                (SpiritEntity spirit) -> spirit.distanceToSqr(home.x() + 0.5, home.y(), home.z() + 0.5))
                        .thenComparing(spirit -> spirit.getUUID().toString()))
                .limit(IntellectTuning.loadDefault().teamMax())
                .map(SpiritEntity::getUUID)
                .toList();
        return new Expedition(team, new Point(home.x() + 0.5, home.y() - 10, home.z() + 0.5), now);
    }
}
