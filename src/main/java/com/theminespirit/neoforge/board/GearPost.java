package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GearInputs;
import com.theminespirit.core.board.graph.GearRules;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;

final class GearPost {
    private static final String EQUIP = "gear_equip_best";
    private static final String ARMOR = "gear_craft_armor";
    private static final String CRAFT = "craft_tool:";

    private GearPost() {}

    static boolean event(GraphPost post, GraphTickets.Ticket ticket) {
        String node = ticket.node();
        if (EQUIP.equals(node)) {
            return equip(post);
        }
        if (ARMOR.equals(node)) {
            return craftArmor(post);
        }
        if (ticket.task().id().startsWith(CRAFT)) {
            return craftTool(post, ticket.task().id().substring(CRAFT.length()));
        }
        return false;
    }

    private static boolean equip(GraphPost post) {
        GearInputs.Settings settings = GearInputs.settings();
        for (SpiritEntity spirit : post.spirits) {
            GraphFacts.Spirit fact = post.facts.spirits().get(spirit.getUUID().toString());
            if (fact == null || busy(post, spirit)) {
                continue;
            }
            Optional<GearRules.Upgrade> upgrade = GearRules.equipUpgrade(fact, post.facts.chests(), settings);
            if (upgrade.isEmpty()) {
                continue;
            }
            GearRules.Upgrade known = upgrade.orElseThrow();
            BlockCoord chest = StorePost.at(known.chest());
            ActivityPlans.Plan plan = ActivityPlans.equip(chest, post.craftPoint(), known.item());
            return post.launchActivity(spirit, BoardCase.ACTIVITY_ARM, plan);
        }
        return false;
    }

    private static boolean craftArmor(GraphPost post) {
        GearInputs.Settings settings = GearInputs.settings();
        int stage = post.scan.stage().stage();
        for (SpiritEntity spirit : post.spirits) {
            GraphFacts.Spirit fact = post.facts.spirits().get(spirit.getUUID().toString());
            if (fact == null || busy(post, spirit)) {
                continue;
            }
            Optional<String> item = GearRules.missingArmor(fact, stage, settings);
            if (item.isEmpty()) {
                continue;
            }
            ActivityPlans.Plan plan = ActivityPlans.craft(post.craftPoint(), item.orElseThrow(), 1);
            return post.launchActivity(spirit, BoardCase.ACTIVITY_CRAFT, plan);
        }
        return false;
    }

    private static boolean craftTool(GraphPost post, String kind) {
        GearInputs.Settings settings = GearInputs.settings();
        int stage = post.scan.stage().stage();
        for (SpiritEntity spirit : post.spirits) {
            GraphFacts.Spirit fact = post.facts.spirits().get(spirit.getUUID().toString());
            if (fact == null || busy(post, spirit) || !GearRules.needsTool(fact, kind, stage, settings)) {
                continue;
            }
            String item = GearRules.craftToolItem(fact, kind, stage, settings);
            ActivityPlans.Plan plan = ActivityPlans.craft(post.craftPoint(), item, 1);
            return post.launchActivity(spirit, BoardCase.ACTIVITY_CRAFT, plan);
        }
        return false;
    }

    private static boolean busy(GraphPost post, SpiritEntity spirit) {
        return !post.service.board().assignedTo(spirit.getUUID()).isEmpty()
                || post.service.board().occupation(spirit.getUUID()).isPresent();
    }
}
