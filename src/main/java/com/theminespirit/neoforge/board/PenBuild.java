package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class PenBuild {
    private PenBuild() {}

    static boolean post(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            GraphTickets.Ticket ticket,
            int priority,
            Map<String, Integer> need,
            long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return false;
        }
        Optional<Pen> pen = pen(ticket.node());
        if (pen.isEmpty()) {
            return false;
        }
        Pen known = pen.orElseThrow();
        String id = ticketId(known.species(), known.level());
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.DONE
                || status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED) {
            return true;
        }
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.BUILD_PEN,
                        Optional.empty(),
                        priority,
                        now,
                        known.level(),
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.copyOf(need)),
                TicketTarget.at(homeOf(spirits)).withSpecies(known.species()));
        return true;
    }

    static boolean built(BoardService service, String node) {
        Optional<Pen> pen = pen(node);
        if (pen.isEmpty()) {
            return false;
        }
        String id = ticketId(pen.orElseThrow().species(), pen.orElseThrow().level());
        return service.board().statusOf(id).orElse(null) == BoardStatus.DONE;
    }

    static String ticketId(String node) {
        return pen(node).map(pen -> ticketId(pen.species(), pen.level())).orElse(node);
    }

    static Optional<Pen> pen(String node) {
        return switch (node) {
            case "chicken_coop" -> Optional.of(new Pen(Species.CHICKEN, 1));
            case "pen_cow" -> Optional.of(new Pen(Species.COW, 1));
            case "pen_sheep" -> Optional.of(new Pen(Species.SHEEP, 1));
            case "pen_pig" -> Optional.of(new Pen(Species.PIG, 1));
            default -> numeric(node);
        };
    }

    private static Optional<Pen> numeric(String node) {
        if (!node.startsWith("pen_")) {
            return Optional.empty();
        }
        try {
            int level = Integer.parseInt(node.substring("pen_".length()));
            return level > 0 ? Optional.of(new Pen(Species.COW, level)) : Optional.empty();
        } catch (NumberFormatException notLevel) {
            return Optional.empty();
        }
    }

    private static String ticketId(Species species, int level) {
        return species.name().toLowerCase(Locale.ROOT) + "_pen_" + level;
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

    record Pen(Species species, int level) {}
}
