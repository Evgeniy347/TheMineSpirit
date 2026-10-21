package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.BreedingRules;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.HerdCap;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdSpecies;
import com.theminespirit.neoforge.husbandry.HerdSurvey;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

/**
 * Источник доски «стадо»: раз в {@link BoardTuning#herdScanIntervalTicks()} ищет диких
 * животных рядом с поселением. Циклы идут по видам независимо: у каждого вида свой отлов,
 * загон, приманка и лимит четырёх голов. Пойманное животное остаётся живой причиной цикла
 * ({@code animal:capture:<вид>:<uuid>}), пока сущность жива.
 */
public final class HerdWatch {
    private static final int HERD_RADIUS = 32;

    private HerdWatch() {}

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        BlockCoord home = homeOf(spirits);
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        List<Animal> animals = HerdSurvey.query(level, center, HERD_RADIUS);
        for (Species species : Species.all()) {
            if (service.capturedCount(species) >= HerdCap.maxPerSpecies()) {
                continue;
            }
            if (hasActive(service, species)) {
                continue;
            }
            Animal wild = wildOf(animals, species);
            if (wild == null) {
                continue;
            }
            if (!penBuilt(service, species)) {
                continue;
            }
            capture(service, species, wild, penTicketId(service, species), now);
        }
        breed(service, home, now);
    }

    private static Animal wildOf(List<Animal> animals, Species species) {
        for (Animal animal : animals) {
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

    private static void capture(
            BoardService service, Species species, Animal animal, Optional<String> penId, long now) {
        String id = captureId(species, animal);
        BlockCoord target = new BlockCoord(
                animal.blockPosition().getX(),
                animal.blockPosition().getY(),
                animal.blockPosition().getZ());
        TicketTarget point = TicketTarget.at(target).withSpecies(species);
        service.noteCapture(id, species, animal.getUUID(), point);
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.CAPTURE_ANIMAL,
                        Optional.empty(),
                        priority(BoardCase.CAPTURE_ANIMAL, service, species, true),
                        now,
                        1,
                        "animal:" + id,
                        penId.map(List::of).orElse(List.of()),
                        Map.of()),
                point);
    }

    private static void breed(BoardService service, BlockCoord home, long now) {
        for (Species species : Species.all()) {
            int housed = service.capturedCount(species);
            boolean penBuilt = penBuilt(service, species);
            BreedingRules.Action action = BreedingRules.action(species, housed, penBuilt);
            if (action == BreedingRules.Action.BREED && !hasActiveCase(service, species, BoardCase.BREED_ANIMALS)) {
                service.submit(
                        new BoardTicket(
                                species.name().toLowerCase(Locale.ROOT) + "_breed",
                                BoardCase.BREED_ANIMALS,
                                Optional.empty(),
                                priority(BoardCase.BREED_ANIMALS, service, species, penBuilt),
                                now,
                                1,
                                causeOf(service, species),
                                List.of(),
                                BreedingRules.breedingGate(species)),
                        TicketTarget.at(home).withSpecies(species));
            } else if (action == BreedingRules.Action.CULL && !hasActiveCase(service, species, BoardCase.HERD_CULL)) {
                service.submit(
                        new BoardTicket(
                                species.name().toLowerCase(Locale.ROOT) + "_cull",
                                BoardCase.HERD_CULL,
                                Optional.empty(),
                                priority(BoardCase.HERD_CULL, service, species, penBuilt),
                                now,
                                1,
                                causeOf(service, species),
                                List.of(),
                                Map.of()),
                        TicketTarget.at(home).withSpecies(species));
            }
        }
    }

    private static int priority(BoardCase boardCase, BoardService service, Species species, boolean penBuilt) {
        PriorityContext context = new PriorityContext(
                service.capturedCount(species) > 0, penBuilt, !baitReady(service, species), false, false);
        return BoardPriorities.effective(boardCase, context);
    }

    private static boolean baitReady(BoardService service, Species species) {
        String feed = FeedTable.feedFor(species).get(0);
        String shortFeed = feed.startsWith("minecraft:") ? feed.substring("minecraft:".length()) : feed;
        return service.stock().available().getOrDefault(shortFeed, 0) > 0;
    }

    static boolean penBuilt(BoardService service, Species species) {
        if (penTicketId(service, species).isPresent()) {
            return true;
        }
        for (int level = 1; level <= BuildingLevels.maxLevel(BoardCase.BUILD_PEN); level++) {
            if (service.board().ledger().has(BoardCase.BUILD_PEN, level)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> penTicketId(BoardService service, Species species) {
        String prefix = species.name().toLowerCase(Locale.ROOT) + "_pen";
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() == BoardCase.BUILD_PEN
                    && ticket.id().startsWith(prefix)
                    && service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.DONE) {
                return Optional.of(ticket.id());
            }
        }
        return Optional.empty();
    }

    private static String causeOf(BoardService service, Species species) {
        String prefix = "animal:capture:" + species.name().toLowerCase(Locale.ROOT) + ":";
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.cause().startsWith(prefix)) {
                return ticket.cause();
            }
        }
        return BoardTicket.ALWAYS_CAUSE;
    }

    private static boolean hasActive(BoardService service, Species species) {
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != BoardCase.CAPTURE_ANIMAL
                    && ticket.boardCase() != BoardCase.BUILD_PEN
                    && ticket.boardCase() != BoardCase.LURE_ANIMAL
                    && ticket.boardCase() != BoardCase.DISMANTLE_SHELTER) {
                continue;
            }
            if (!ticket.id().startsWith(species.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (active(service, ticket.id())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveCase(BoardService service, Species species, BoardCase boardCase) {
        String prefix = species.name().toLowerCase(Locale.ROOT);
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() == boardCase && ticket.id().startsWith(prefix) && active(service, ticket.id())) {
                return true;
            }
        }
        return false;
    }

    private static boolean active(BoardService service, String id) {
        return service.board()
                .statusOf(id)
                .map(status -> status == BoardStatus.PENDING
                        || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .orElse(false);
    }

    private static String captureId(Species species, Animal animal) {
        return "capture:" + species.name().toLowerCase(Locale.ROOT) + ":" + animal.getUUID();
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                return home.orElseThrow();
            }
        }
        return new BlockCoord(0, 64, 0);
    }
}
