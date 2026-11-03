package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BreedingRules;
import com.theminespirit.core.board.HerdCap;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WoolRules;
import com.theminespirit.core.board.graph.FarmWays;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.husbandry.HerdSpecies;
import com.theminespirit.neoforge.husbandry.HerdSurvey;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class FarmHerd {
    private static final int HERD_RADIUS = 32;

    private FarmHerd() {}

    static boolean capture(GraphPost post, GraphTickets.Ticket ticket) {
        Species species = FarmWays.species(ticket.node());
        if (species == null) {
            return false;
        }
        BlockCoord home = post.scan.home();
        int housed = count(post.level, home, species);
        if (!HerdCap.canCapture(species, housed, 0)) {
            return false;
        }
        Animal wild = wild(post.level, home, species);
        if (wild == null) {
            return false;
        }
        TicketTarget target = TicketTarget.at(home).withSpecies(species);
        post.service.noteCapture(ticket.id(), species, wild.getUUID(), target);
        FarmPost.submit(
                post, ticket.id(), BoardCase.CAPTURE_ANIMAL, ticket.task().priority(), target, Map.of());
        return true;
    }

    static boolean breed(GraphPost post, GraphTickets.Ticket ticket) {
        Species species = FarmWays.species(ticket.node());
        if (species == null) {
            return false;
        }
        BlockCoord home = post.scan.home();
        int housed = count(post.level, home, species);
        if (BreedingRules.action(species, housed, true) != BreedingRules.Action.BREED) {
            return false;
        }
        TicketTarget target = TicketTarget.at(home).withSpecies(species);
        FarmPost.submit(
                post, ticket.id(), BoardCase.BREED_ANIMALS, ticket.task().priority(), target, FarmPost.gate(species));
        return true;
    }

    static boolean cull(GraphPost post, GraphTickets.Ticket ticket) {
        Species species = FarmWays.species(ticket.node());
        if (species == null) {
            return false;
        }
        BlockCoord home = post.scan.home();
        int housed = count(post.level, home, species);
        if (BreedingRules.action(species, housed, true) != BreedingRules.Action.CULL) {
            return false;
        }
        TicketTarget target = TicketTarget.at(home).withSpecies(species);
        FarmPost.submit(post, ticket.id(), BoardCase.HERD_CULL, ticket.task().priority(), target, Map.of());
        return true;
    }

    static boolean shear(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord home = post.scan.home();
        int sheep = unshornInPen(post.level, post.service, home);
        if (!WoolRules.penReady(sheep)) {
            return false;
        }
        int iron = post.stock.available().getOrDefault(WoolRules.IRON_INGOT, 0);
        boolean hasShears = post.stock.available().getOrDefault(WoolRules.SHEARS, 0) > 0;
        if (!hasShears) {
            if (!WoolRules.shearsCraftable(iron, false)) {
                return false;
            }
            FarmPost.submit(
                    post,
                    ticket.id() + ":shears",
                    BoardCase.CRAFT_SHEARS,
                    ticket.task().priority(),
                    TicketTarget.at(home),
                    Map.of(WoolRules.IRON_INGOT, WoolRules.shearsIron()));
            return true;
        }
        int count = Math.min(Math.max(1, ticket.count()), sheep);
        TicketTarget target = TicketTarget.at(home).withSpecies(Species.SHEEP).withCount(count);
        FarmPost.submit(
                post,
                ticket.id(),
                BoardCase.SHEAR_SHEEP,
                ticket.task().priority(),
                target,
                Map.of(WoolRules.SHEARS, 1));
        return true;
    }

    static boolean hunt(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord home = post.scan.home();
        int wild = wildSheep(post.level, post.service, home);
        if (wild <= 0) {
            return false;
        }
        int count = Math.min(Math.max(1, ticket.count()), wild);
        TicketTarget target = TicketTarget.at(home).withSpecies(Species.SHEEP).withCount(count);
        FarmPost.submit(
                post, ticket.id(), BoardCase.HUNT_WILD_SHEEP, ticket.task().priority(), target, Map.of());
        return true;
    }

    private static int unshornInPen(ServerLevel level, BoardService service, BlockCoord home) {
        Set<UUID> own = service.capturedAnimals(Species.SHEEP);
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        int radius = WoolRules.shearRadius();
        int count = 0;
        for (Animal animal : HerdSurvey.query(level, center, radius)) {
            if (!(animal instanceof Sheep sheep)
                    || animal.isBaby()
                    || sheep.isSheared()
                    || !animal.isAlive()
                    || !own.contains(animal.getUUID())) {
                continue;
            }
            if (distanceSquared(home, animal) <= (long) radius * radius) {
                count++;
            }
        }
        return count;
    }

    private static int wildSheep(ServerLevel level, BoardService service, BlockCoord home) {
        Set<UUID> own = service.capturedAnimals(Species.SHEEP);
        int radius = WoolRules.wildSheepRadius();
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        int count = 0;
        for (Animal animal : HerdSurvey.query(level, center, radius)) {
            if (!(animal instanceof Sheep) || !animal.isAlive() || own.contains(animal.getUUID())) {
                continue;
            }
            long distance = distanceSquared(home, animal);
            if (WoolRules.wildTarget(false, distance, radius)) {
                count++;
            }
        }
        return count;
    }

    private static int count(ServerLevel level, BlockCoord home, Species species) {
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        int count = 0;
        for (Animal animal : HerdSurvey.query(level, center, HERD_RADIUS)) {
            if (animal.isRemoved() || !animal.isAlive()) {
                continue;
            }
            Optional<Species> found = HerdSpecies.of(animal);
            if (found.isPresent() && found.orElseThrow() == species) {
                count++;
            }
        }
        return count;
    }

    private static @Nullable Animal wild(ServerLevel level, BlockCoord home, Species species) {
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        for (Animal animal : HerdSurvey.query(level, center, HERD_RADIUS)) {
            if (animal.isRemoved() || !animal.isAlive() || animal.isInLove()) {
                continue;
            }
            Optional<Species> found = HerdSpecies.of(animal);
            if (found.isPresent() && found.orElseThrow() == species) {
                return animal;
            }
        }
        return null;
    }

    private static long distanceSquared(BlockCoord home, Animal animal) {
        long dx = (long) animal.blockPosition().getX() - home.x();
        long dy = (long) animal.blockPosition().getY() - home.y();
        long dz = (long) animal.blockPosition().getZ() - home.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
