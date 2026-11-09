package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WoolRules;
import com.theminespirit.core.board.graph.FarmWays;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.fishing.FishingNeeds;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import java.util.Optional;

final class FarmWork {
    private FarmWork() {}

    static boolean harvestHive(GraphPost post, GraphTickets.Ticket ticket) {
        if (!HiveSite.harvestable(post.stock)) {
            return false;
        }
        Optional<BlockCoord> hive = HiveSite.harvest(post.level, post.scan.home());
        if (hive.isEmpty()) {
            return false;
        }
        FarmPost.submit(
                post,
                ticket.id(),
                BoardCase.HARVEST_HIVE,
                ticket.task().priority(),
                TicketTarget.at(hive.orElseThrow()),
                HiveSite.harvestNeed(post.stock));
        return true;
    }

    static boolean fish(GraphPost post, GraphTickets.Ticket ticket) {
        double share = FishingNeeds.foodShare(post.stock.available(), post.spirits.size());
        if (!FishingNeeds.wantsFishing(share)) {
            return false;
        }
        Optional<BlockCoord> spot = FishingSite.workPoint(post.level, post.scan.home());
        if (spot.isEmpty()) {
            return false;
        }
        post.submit(ticket, BoardCase.FISH, TicketTarget.at(spot.orElseThrow()), Map.of());
        return true;
    }

    static boolean bed(GraphPost post, GraphTickets.Ticket ticket) {
        if (FarmWays.bed(ticket.node()) == null) {
            return false;
        }
        Optional<String> color = WoolRules.bedColor(post.stock);
        Optional<String> planks = WoolRules.planksType(post.stock);
        if (color.isEmpty() || planks.isEmpty()) {
            return false;
        }
        BlockCoord home = post.scan.home();
        int needed = WoolRules.bedsNeeded(WoolRules.bedCount(post.stock), post.spirits.size());
        int count = Math.max(1, Math.min(ticket.count(), needed));
        TicketTarget target = TicketTarget.at(home)
                .withCraft(WoolRules.bedId(color.orElseThrow()), count)
                .withCount(count);
        Map<String, Integer> required = Map.of(
                WoolRules.woolId(color.orElseThrow()),
                WoolRules.bedWool(),
                planks.orElseThrow(),
                WoolRules.bedPlanks());
        FarmPost.submit(post, ticket.id(), BoardCase.CRAFT_BED, ticket.task().priority(), target, required);
        return true;
    }

    static boolean weave(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord home = post.scan.home();
        int wool = WoolRules.woolFromStrings(post.stock.available().getOrDefault(WoolRules.STRING, 0));
        if (wool <= 0) {
            return false;
        }
        int count = Math.min(Math.max(1, ticket.count()), wool);
        TicketTarget target =
                TicketTarget.at(home).withCraft(WoolRules.CRAFTED_WOOL, count).withCount(count);
        FarmPost.submit(
                post,
                ticket.id(),
                BoardCase.WEAVE_WOOL,
                ticket.task().priority(),
                target,
                Map.of(WoolRules.STRING, count * WoolRules.STRINGS_PER_WOOL));
        return true;
    }
}
