package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Applicability;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BreedingRules;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.FarmRules;
import com.theminespirit.core.board.graph.FarmWays;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.fishing.FishingNeeds;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.husbandry.PetKind;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.FarmActions;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdSpecies;
import com.theminespirit.neoforge.husbandry.HerdSurvey;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class FarmPost {
    private static final int HERD_RADIUS = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog HOE = new StateChangeLog();

    private FarmPost() {}

    static boolean pet(GraphPost post, GraphTickets.Ticket ticket) {
        PetKind kind = FarmWays.pet(ticket.node());
        if (kind == null) {
            return false;
        }
        return post.pets.tame(post.level, post.spirits, kind);
    }

    static boolean way(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        if (special(post, ticket)) {
            return true;
        }
        BoardCase boardCase = task.boardCase();
        return switch (boardCase) {
            case CAPTURE_ANIMAL, LURE_ANIMAL -> FarmHerd.capture(post, ticket);
            case BREED_ANIMALS -> petBreed(post, ticket) || FarmHerd.breed(post, ticket);
            case HERD_CULL -> FarmHerd.cull(post, ticket);
            case SHEAR_SHEEP -> FarmHerd.shear(post, ticket);
            case HUNT_WILD_SHEEP -> FarmHerd.hunt(post, ticket);
            case HARVEST_HIVE -> FarmWork.harvestHive(post, ticket);
            case FISH -> FarmWork.fish(post, ticket);
            case CRAFT_BED -> FarmWork.bed(post, ticket);
            case WEAVE_WOOL -> FarmWork.weave(post, ticket);
            default -> false;
        };
    }

    static boolean handlesEvent(String node) {
        return FarmRules.handlesEvent(node);
    }

    static boolean event(GraphPost post, GraphTickets.Ticket ticket) {
        String node = ticket.node();
        if (!handlesEvent(node)) {
            return false;
        }
        if ("harvest_crop".equals(node)) {
            return launch(post, ActivityPlans.harvestCrop(post.scan.home()));
        }
        if ("plant_crop".equals(node)) {
            return launch(post, ActivityPlans.plantCrop(post.scan.home(), "wheat", 8));
        }
        BlockCoord home = post.scan.home();
        Species species = herdSpecies(post.level, home);
        if (species == null) {
            return false;
        }
        TicketTarget target = TicketTarget.at(home).withSpecies(species);
        submit(post, ticket.id(), BoardCase.BREED_ANIMALS, ticket.task().priority(), target, gate(species));
        return true;
    }

    private static boolean special(GraphPost post, GraphTickets.Ticket ticket) {
        return switch (ticket.node()) {
            case "till_soil" -> {
                till(post, ticket.id());
                yield true;
            }
            case "plant_crop" -> launch(post, ActivityPlans.plantCrop(post.scan.home(), "wheat", 8));
            case "harvest_crop" -> launch(post, ActivityPlans.harvestCrop(post.scan.home()));
            case "milk_animal" ->
                post.stock.available().getOrDefault("bucket", 0) > 0
                        && launch(post, ActivityPlans.milk(post.scan.home(), 1));
            case "feed_animals" -> {
                Species species = herdSpecies(post.level, post.scan.home());
                yield species != null && FarmHerd.breed(post, ticket, species);
            }
            case "lure_animal" -> {
                Species species = FarmHerd.wildSpecies(post.level, post.scan.home());
                yield species != null && FarmHerd.capture(post, ticket, species);
            }
            default -> false;
        };
    }

    private static boolean till(GraphPost post, String ticketId) {
        SpiritEntity spirit = idleWithHoe(post);
        if (spirit == null) {
            SpiritEntity idle = StorePost.idle(post).orElse(null);
            if (idle != null && HOE.changed("till:" + idle.getUUID(), "нет")) {
                LOGGER.info(
                        "[ферма] нужна тяпка {}: нет — граф ставит её крафт (узел craft_tool:hoe)", idle.displayName());
            }
            post.waiting().add(ticketId);
            return false;
        }
        if (HOE.changed("till:" + spirit.getUUID(), "есть")) {
            LOGGER.info("[ферма] нужна тяпка {}: взял — пашу", spirit.displayName());
        }
        return post.launchActivity(spirit, BoardCase.ACTIVITY_FARM, ActivityPlans.till(post.scan.home()));
    }

    private static @Nullable SpiritEntity idleWithHoe(GraphPost post) {
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.isRemoved()
                    || !post.service.board().assignedTo(spirit.getUUID()).isEmpty()
                    || post.service.board().occupation(spirit.getUUID()).isPresent()) {
                continue;
            }
            if (new FarmActions(spirit).hasSupplies(FarmJob.TILL, CropKind.ANY)) {
                return spirit;
            }
        }
        return null;
    }

    private static boolean launch(GraphPost post, ActivityPlans.Plan plan) {
        SpiritEntity spirit = StorePost.idle(post).orElse(null);
        if (spirit == null) {
            return false;
        }
        return post.launchActivity(spirit, BoardCase.ACTIVITY_FARM, plan);
    }

    static boolean building(GraphPost post, GraphTickets.Ticket ticket) {
        String node = ticket.node();
        BlockCoord home = post.scan.home();
        if ("chicken_coop".equals(node)) {
            TicketTarget target = TicketTarget.at(home).withSpecies(Species.CHICKEN);
            submit(
                    post,
                    ticket.id(),
                    BoardCase.BUILD_PEN,
                    ticket.task().priority(),
                    target,
                    need(post, BoardCase.BUILD_PEN, target));
            return true;
        }
        if ("pen_cow".equals(node) || "pen_sheep".equals(node) || "pen_pig".equals(node)) {
            Species species = FarmWays.species(node.substring("pen_".length()));
            if (species == null) {
                return false;
            }
            TicketTarget target = TicketTarget.at(home).withSpecies(species);
            submit(
                    post,
                    ticket.id(),
                    BoardCase.BUILD_PEN,
                    ticket.task().priority(),
                    target,
                    need(post, BoardCase.BUILD_PEN, target));
            return true;
        }
        if ("apiary".equals(node)) {
            if (HiveSite.hiveNear(post.level, home)) {
                return true;
            }
            Optional<BlockCoord> site = HiveSite.place(post.level, home);
            Optional<Map<String, Integer>> need = HiveSite.placeNeed(post.stock);
            if (site.isEmpty() || need.isEmpty()) {
                return false;
            }
            submit(
                    post,
                    ticket.id(),
                    BoardCase.PLACE_BEEHIVE,
                    ticket.task().priority(),
                    TicketTarget.at(site.orElseThrow()),
                    need.orElseThrow());
            return true;
        }
        if ("storage_space".equals(node)) {
            return ChestSpaceWatch.post(post);
        }
        if ("smoker".equals(node)) {
            if (!FishingNeeds.wantsSmoker(
                            post.stock.available(),
                            DataTables.INSTANCE.fishing().current())
                    || FishingSite.smokerNear(post.level, home)) {
                return true;
            }
            TicketTarget target = TicketTarget.at(home);
            submit(
                    post,
                    ticket.id(),
                    BoardCase.CRAFT_SMOKER,
                    ticket.task().priority(),
                    target,
                    need(post, BoardCase.CRAFT_SMOKER, target));
            return true;
        }
        return false;
    }

    static void eat(GraphPost post, GraphTickets.Ticket ticket) {
        for (SpiritEntity spirit : post.spirits) {
            if (!needsEat(spirit)) {
                continue;
            }
            post.submit(
                    post.ticket(
                            ticket.id() + "@" + spirit.getUUID(),
                            BoardCase.EAT_TO_FULL,
                            Optional.of(spirit.getUUID()),
                            ticket.task().priority(),
                            ticket.id(),
                            Map.of()),
                    TicketTarget.at(new BlockCoord(
                            spirit.blockPosition().getX(),
                            spirit.blockPosition().getY(),
                            spirit.blockPosition().getZ())));
        }
    }

    private static boolean needsEat(SpiritEntity spirit) {
        EatNeed need = new EatNeed(
                spirit.hunger().foodLevel(), (int) Math.ceil(spirit.getHealth()), SpiritHunger.MAX_FOOD, (int)
                        Math.ceil(spirit.getMaxHealth()));
        SpiritView view = new SpiritView(
                spirit.tactics().wantsControl(), false, spirit.taskStatus().orElse(null) == RunStatus.RUNNING);
        return spirit.hunger().hasFood() && !need.full() && Applicability.applicable(BoardCase.EAT_TO_FULL, need, view);
    }

    private static boolean petBreed(GraphPost post, GraphTickets.Ticket ticket) {
        PetKind kind = FarmWays.pet(ticket.node());
        return kind != null && post.pets.breed(post.level, post.spirits, kind, post.now);
    }

    static void submit(
            GraphPost post,
            String id,
            BoardCase boardCase,
            int priority,
            TicketTarget target,
            Map<String, Integer> required) {
        post.submit(post.ticket(id, boardCase, Optional.empty(), priority, id, required), target);
    }

    private static Map<String, Integer> need(GraphPost post, BoardCase boardCase, TicketTarget target) {
        try {
            return CaseMaterials.need(TicketPlans.plan(boardCase, 1, target), post.source);
        } catch (RuntimeException failure) {
            return Map.of();
        }
    }

    static Map<String, Integer> gate(Species species) {
        return BreedingRules.breedingGate(species);
    }

    private static @Nullable Species herdSpecies(ServerLevel level, BlockCoord home) {
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        for (Animal animal : HerdSurvey.query(level, center, HERD_RADIUS)) {
            Optional<Species> found = HerdSpecies.of(animal);
            if (found.isPresent()) {
                return found.orElseThrow();
            }
        }
        return null;
    }
}
