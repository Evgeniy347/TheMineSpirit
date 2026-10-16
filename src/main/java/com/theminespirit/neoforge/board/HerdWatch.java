package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.TicketTarget;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

/**
 * Источник доски «животное найдено»: раз в {@link BoardTuning#herdScanIntervalTicks()}
 * ищет дикое животное рядом с поселением и ставит {@code CAPTURE_ANIMAL}.
 */
public final class HerdWatch {
    private static final int HERD_RADIUS = 32;

    private HerdWatch() {
    }

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        if (hasActiveCapture(service)) {
            return;
        }
        BlockCoord home = homeOf(spirits);
        Vec3 center = new Vec3(home.x() + 0.5, home.y(), home.z() + 0.5);
        List<Animal> animals = HerdSurvey.query(level, center, HERD_RADIUS);
        for (Animal animal : animals) {
            if (animal.isRemoved() || !animal.isAlive()) {
                continue;
            }
            if (animal.isInLove()) {
                continue;
            }
            Optional<Species> species = HerdSpecies.of(animal);
            if (species.isEmpty()) {
                continue;
            }
            String id = "capture:" + species.orElseThrow().name().toLowerCase(Locale.ROOT) + ":"
                    + animal.blockPosition().getX() + "," + animal.blockPosition().getY() + ","
                    + animal.blockPosition().getZ();
            BlockCoord target = new BlockCoord(animal.blockPosition().getX(), animal.blockPosition().getY(),
                    animal.blockPosition().getZ());
            service.submit(new BoardTicket(id, BoardCase.CAPTURE_ANIMAL, Optional.empty(),
                    BoardRules.priorityOf(BoardCase.CAPTURE_ANIMAL), now, 1,
                    animalCause(species.orElseThrow(), animal.blockPosition()), List.of(), Map.of()),
                    TicketTarget.at(target).withSpecies(species.orElseThrow()));
            return;
        }
    }

    private static boolean hasActiveCapture(BoardService service) {
        return service.board().all().stream().anyMatch(ticket -> {
            if (ticket.boardCase() != BoardCase.CAPTURE_ANIMAL
                    && ticket.boardCase() != BoardCase.BUILD_PEN
                    && ticket.boardCase() != BoardCase.LURE_ANIMAL
                    && ticket.boardCase() != BoardCase.DISMANTLE_SHELTER) {
                return false;
            }
            return service.board().statusOf(ticket.id())
                    .map(s -> s == BoardStatus.PENDING || s == BoardStatus.WAITING_RESOURCES
                            || s == BoardStatus.ASSIGNED)
                    .orElse(false);
        });
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

    /** Причина задания отлова — вид и клетка животного: погибшее уходит из живых причин. */
    static String animalCause(Species species, BlockPos pos) {
        return "animal:" + species.name().toLowerCase(Locale.ROOT) + ":" + pos.getX() + ":"
                + pos.getY() + ":" + pos.getZ();
    }
}
