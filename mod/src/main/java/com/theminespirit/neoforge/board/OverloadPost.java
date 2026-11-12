package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class OverloadPost {
    static final String EVENT = "inventory_overloaded";
    static final String NODE = "unload_overloaded";

    private OverloadPost() {}

    static boolean pending(List<SpiritEntity> spirits) {
        return mostLoaded(spirits).isPresent();
    }

    static boolean handles(String node) {
        return NODE.equals(node) || EVENT.equals(node);
    }

    static boolean post(GraphPost post) {
        Optional<SpiritEntity> loaded = mostLoaded(post.spirits);
        if (loaded.isEmpty()) {
            return true;
        }
        SpiritEntity spirit = loaded.orElseThrow();
        Optional<GraphFacts.Chest> chest = chest(post.facts.chests(), spirit);
        if (chest.isEmpty()) {
            return false;
        }
        GraphFacts.Position at = chest.orElseThrow().position();
        return GraphActivityPost.launch(
                post, spirit, BoardCase.ACTIVITY_UNLOAD, ActivityPlans.unload(new BlockCoord(at.x(), at.y(), at.z())));
    }

    static Optional<SpiritEntity> mostLoaded(List<SpiritEntity> spirits) {
        int minFreePercent = DataTables.INSTANCE.graph().current().storage().minFreePercent();
        return spirits.stream()
                .filter(spirit -> !spirit.isRemoved())
                .filter(spirit ->
                        overloaded(occupied(spirit), spirit.getInventory().getContainerSize(), minFreePercent))
                .max(Comparator.comparingInt(OverloadPost::occupied));
    }

    static boolean overloaded(int occupiedSlots, int slots, int minFreePercent) {
        return (slots - occupiedSlots) * 100 < slots * minFreePercent;
    }

    static Optional<GraphFacts.Chest> chest(List<GraphFacts.Chest> chests, SpiritEntity spirit) {
        return chests.stream()
                .filter(chest -> chest.freeSlots() > 0)
                .filter(chest -> chest.reach() != GraphFacts.Reach.NO_PATH)
                .min(Comparator.<GraphFacts.Chest>comparingInt(chest -> chest.reach() == GraphFacts.Reach.PATH ? 0 : 1)
                        .thenComparingDouble(chest -> spirit.distanceToSqr(
                                chest.position().x(),
                                chest.position().y(),
                                chest.position().z())));
    }

    private static int occupied(SpiritEntity spirit) {
        int used = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (!spirit.getInventory().getItem(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }
}
