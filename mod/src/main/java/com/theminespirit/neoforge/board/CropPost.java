package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;

final class CropPost {
    private static final Map<String, String> FIELD = Map.of(
            "harvest_carrot", "carrot",
            "harvest_potato", "potato",
            "harvest_beetroot", "beetroot",
            "harvest_beetroot_seeds", "beetroot");
    private static final Map<String, String> WILD = Map.of(
            "forage_carrot", "carrot",
            "forage_potato", "potato",
            "forage_beetroot", "beetroot",
            "forage_beetroot_seeds", "beetroot");
    private static final String WILD_TAG = "crop:";

    private CropPost() {}

    static boolean way(GraphPost post, GraphTickets.Ticket ticket) {
        String crop = FIELD.get(ticket.node());
        if (crop != null) {
            return launch(post, ActivityPlans.harvestCrop(post.scan.home(), crop));
        }
        crop = WILD.get(ticket.node());
        if (crop == null) {
            return false;
        }
        Optional<BlockCoord> wild = post.around.resource(WILD_TAG + crop);
        return wild.isPresent() && launch(post, ActivityPlans.harvestCrop(wild.orElseThrow(), crop));
    }

    private static boolean launch(GraphPost post, ActivityPlans.Plan plan) {
        SpiritEntity spirit = StorePost.idle(post).orElse(null);
        return spirit != null && GraphActivityPost.launch(post, spirit, BoardCase.ACTIVITY_FARM, plan);
    }
}
