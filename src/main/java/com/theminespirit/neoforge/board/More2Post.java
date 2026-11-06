package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.MorePlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WoolRules;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdSpecies;
import com.theminespirit.neoforge.husbandry.HerdSurvey;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class More2Post {
    private static final int ANIMAL_RADIUS = 48;
    private static final int VILLAGER_RADIUS = 48;
    private static final String MORE2_TRAIL = "more2_trail";

    private More2Post() {}

    static boolean handles(String node) {
        return node.startsWith("more2_");
    }

    static boolean building(GraphPost post, GraphTickets.Ticket ticket) {
        if (!handles(ticket.node()) || post.open(ticket.id()) || post.done(ticket.id())) {
            return handles(ticket.node());
        }
        return switch (ticket.node()) {
            case "more2_field_chest" ->
                MoreActions.submit(post, ticket, BoardCase.STOCK_CHESTS, TicketTarget.at(home(post)));
            case MORE2_TRAIL -> trail(post, ticket);
            default -> false;
        };
    }

    static boolean way(GraphPost post, GraphTickets.Ticket ticket) {
        if (!handles(ticket.node())) {
            return false;
        }
        return switch (ticket.node()) {
            case "more2_sow_food" -> sowFood(post, ticket);
            case "more2_breed_food" -> breedFood(post, ticket);
            case "more2_unload_carry" ->
                MoreActions.script(post, ticket, BoardCase.ACTIVITY_UNLOAD, home(post), MorePlans.unload(true));
            case "more2_put_out_fire" -> MoreHazards.post(post, ticket, MoreHazards.Kind.FIRE);
            case "more2_remove_lava" -> MoreHazards.post(post, ticket, MoreHazards.Kind.LAVA);
            case "more2_block_flow" -> MoreHazards.post(post, ticket, MoreHazards.Kind.FLOW);
            case "more2_pen_animal" -> herd(post, ticket, true);
            case "more2_lure_wild" -> herd(post, ticket, false);
            case "more2_guard_villager" -> guardVillager(post, ticket);
            case "more2_prepare_stage" -> prepareStage(post, ticket);
            case "more2_make_bed" -> makeBed(post, ticket);
            case "more2_build_trail" -> trail(post, ticket);
            default -> false;
        };
    }

    private static boolean sowFood(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord at = home(post);
        return MoreActions.script(post, ticket, BoardCase.ACTIVITY_FARM, at, MorePlans.plantCrop(at, "wheat"));
    }

    private static boolean breedFood(GraphPost post, GraphTickets.Ticket ticket) {
        Species species = herdSpecies(post, home(post));
        if (species == null) {
            return false;
        }
        TicketTarget target = TicketTarget.at(home(post)).withSpecies(species);
        return MoreActions.submit(post, ticket, BoardCase.BREED_ANIMALS, target, FarmPost.gate(species));
    }

    private static boolean herd(GraphPost post, GraphTickets.Ticket ticket, boolean penned) {
        BlockCoord at = home(post);
        Species species = herdSpecies(post, at);
        if (species == null) {
            return false;
        }
        BoardCase boardCase = penned ? BoardCase.LURE_ANIMAL : BoardCase.CAPTURE_ANIMAL;
        return MoreActions.submit(post, ticket, boardCase, TicketTarget.at(at).withSpecies(species));
    }

    private static Species herdSpecies(GraphPost post, BlockCoord home) {
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        for (Animal animal : HerdSurvey.query(post.level, center, ANIMAL_RADIUS)) {
            if (animal.isRemoved() || !animal.isAlive()) {
                continue;
            }
            Optional<Species> found = HerdSpecies.of(animal);
            if (found.isPresent()) {
                return found.orElseThrow();
            }
        }
        return null;
    }

    private static boolean guardVillager(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord home = home(post);
        BlockPos center = MoreScan.pos(home);
        Optional<Villager> villager =
                post.level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(VILLAGER_RADIUS)).stream()
                        .findFirst();
        if (villager.isEmpty()) {
            return false;
        }
        BlockCoord at = MoreScan.coord(villager.orElseThrow().blockPosition());
        return MoreActions.script(post, ticket, BoardCase.ACTIVITY_GUARD, at, MorePlans.patrol(at));
    }

    private static boolean prepareStage(GraphPost post, GraphTickets.Ticket ticket) {
        String wood = post.scan.wood();
        String item = wood == null || wood.isBlank() ? "oak_log" : wood + "_log";
        return MoreActions.submit(
                post,
                ticket,
                BoardCase.GATHER_MATERIALS,
                TicketTarget.at(home(post)).withSupply(item, 4));
    }

    private static boolean makeBed(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<String> color = WoolRules.bedColor(post.stock);
        Optional<String> planks = WoolRules.planksType(post.stock);
        if (color.isEmpty() || planks.isEmpty()) {
            return false;
        }
        int needed = WoolRules.bedsNeeded(WoolRules.bedCount(post.stock), post.spirits.size());
        int count = Math.max(1, Math.min(ticket.count(), needed));
        TicketTarget target = TicketTarget.at(home(post))
                .withCraft(WoolRules.bedId(color.orElseThrow()), count)
                .withCount(count);
        Map<String, Integer> required = Map.of(
                WoolRules.woolId(color.orElseThrow()),
                WoolRules.bedWool(),
                planks.orElseThrow(),
                WoolRules.bedPlanks());
        return MoreActions.submit(post, ticket, BoardCase.CRAFT_BED, target, required);
    }

    private static boolean trail(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord from = post.scan.stage().anchor();
        BlockCoord to = trailEnd(post, from);
        if (to == null) {
            return false;
        }
        PlotLeveling plan = RouteWorks.path(post.level, from, to, PlotLeveling.DEFAULT_BLOCK);
        if (plan.fill().isEmpty()) {
            return false;
        }
        TicketTarget target = TicketTarget.at(to).withRelocate(from, to).withLeveling(plan);
        return MoreActions.submit(post, ticket, BoardCase.BUILD_STREETS, target);
    }

    private static BlockCoord trailEnd(GraphPost post, BlockCoord from) {
        for (SpiritEntity spirit : post.spirits) {
            BlockPos at = spirit.blockPosition();
            if (at.distSqr(MoreScan.pos(from)) > 8L * 8L) {
                return MoreScan.coord(at);
            }
        }
        return null;
    }

    private static BlockCoord home(GraphPost post) {
        return post.scan.home();
    }
}
