package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GearInputs;
import com.theminespirit.core.board.graph.GearRules;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

final class GearPost {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GearPost() {}

    static boolean handles(String node) {
        return GearInputs.handlesTask(node);
    }

    static boolean event(GraphPost post, GraphTickets.Ticket ticket) {
        String node = ticket.node();
        if (!handles(node)) {
            return false;
        }
        if (GearInputs.EQUIP_TASK.equals(node)) {
            return equip(post, ticket);
        }
        if (GearInputs.ARMOR_TASK.equals(node)) {
            return craftArmor(post, ticket);
        }
        return craftTool(post, ticket, node.substring(GearInputs.TOOL_TASK.length()));
    }

    private static boolean equip(GraphPost post, GraphTickets.Ticket ticket) {
        GearInputs.Settings settings = GearInputs.settings();
        for (SpiritEntity spirit : post.spirits) {
            GraphFacts.Spirit fact = post.facts.spirits().get(spirit.getUUID().toString());
            if (fact == null) {
                continue;
            }
            Optional<GearRules.Upgrade> upgrade = GearRules.equipUpgrade(fact, post.facts.chests(), settings);
            if (upgrade.isEmpty()) {
                continue;
            }
            if (busy(post, spirit)) {
                post.waiting().add(ticket.id());
                return false;
            }
            GearRules.Upgrade known = upgrade.orElseThrow();
            BlockCoord chest = StorePost.at(known.chest());
            ActivityPlans.Plan plan = ActivityPlans.equip(chest, post.craftPoint(), known.item());
            return GraphActivityPost.launch(post, spirit, BoardCase.ACTIVITY_ARM, plan);
        }
        return false;
    }

    private static boolean craftArmor(GraphPost post, GraphTickets.Ticket ticket) {
        GearInputs.Settings settings = GearInputs.settings();
        int stage = post.scan.stage().stage();
        for (SpiritEntity spirit : post.spirits) {
            GraphFacts.Spirit fact = post.facts.spirits().get(spirit.getUUID().toString());
            if (fact == null) {
                continue;
            }
            Optional<String> item = GearRules.missingArmor(fact, stage, settings);
            if (item.isEmpty()) {
                continue;
            }
            if (busy(post, spirit)) {
                post.waiting().add(ticket.id());
                return false;
            }
            ActivityPlans.Plan plan = ActivityPlans.craft(post.craftPoint(), item.orElseThrow(), 1);
            return GraphActivityPost.launch(post, spirit, BoardCase.ACTIVITY_CRAFT, plan);
        }
        return false;
    }

    private static boolean craftTool(GraphPost post, GraphTickets.Ticket ticket, String kind) {
        GearInputs.Settings settings = GearInputs.settings();
        int stage = post.scan.stage().stage();
        for (SpiritEntity spirit : post.spirits) {
            GraphFacts.Spirit fact = post.facts.spirits().get(spirit.getUUID().toString());
            if (fact == null || !GearRules.needsTool(fact, kind, stage, settings)) {
                continue;
            }
            String item = GearRules.craftToolItem(fact, kind, stage, settings);
            BoardCase boardCase = ToolSet.caseFor(item).orElse(BoardCase.GATHER_MATERIALS);
            TicketTarget target = TicketTarget.at(post.craftPoint())
                    .withCraft(item, 1)
                    .withCount(1)
                    .withToolCount(1)
                    .withWood(post.scan.wood());
            post.submit(
                    post.ticket(
                            ticket.id(),
                            boardCase,
                            Optional.of(spirit.getUUID()),
                            ticket.task().priority(),
                            ticket.id(),
                            Map.of()),
                    target);
            LOGGER.info("[снаряжение] нужна {} {}: нет — крафчу {}", kind, spirit.displayName(), item);
            return true;
        }
        return false;
    }

    private static boolean busy(GraphPost post, SpiritEntity spirit) {
        return !post.service.board().assignedTo(spirit.getUUID()).isEmpty()
                || post.service.board().occupation(spirit.getUUID()).isPresent();
    }
}
