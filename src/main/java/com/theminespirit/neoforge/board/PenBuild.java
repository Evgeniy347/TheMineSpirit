package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class PenBuild {
    private static final String[] SCHEMES = {"", "pen_4x5", "pen_7x7", "pen_9x9"};

    private PenBuild() {}

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        int max = BuildingLevels.maxLevel(BoardCase.BUILD_PEN);
        for (Species species : Species.all()) {
            int done = doneLevel(service, species);
            int next = done + 1;
            if (next > max || active(service, species, next)) {
                continue;
            }
            if (done == 0) {
                continue;
            }
            BlockCoord home = homeOf(spirits);
            String id = species.name().toLowerCase(Locale.ROOT) + "_pen_" + next;
            Map<String, Integer> materials = materials(SCHEMES[next]);
            TicketTarget target = TicketTarget.at(home).withSpecies(species);
            service.submit(
                    new BoardTicket(
                            id,
                            BoardCase.BUILD_PEN,
                            Optional.empty(),
                            BoardPriorities.effective(BoardCase.BUILD_PEN, PriorityContext.none()),
                            now,
                            next,
                            causeOf(service, species),
                            List.of(),
                            materials),
                    target);
        }
    }

    private static int doneLevel(BoardService service, Species species) {
        int done = 0;
        String prefix = species.name().toLowerCase(Locale.ROOT) + "_pen";
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != BoardCase.BUILD_PEN || !ticket.id().startsWith(prefix)) {
                continue;
            }
            if (service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.DONE) {
                done = Math.max(done, ticket.level());
            }
        }
        return done;
    }

    private static boolean active(BoardService service, Species species, int level) {
        String id = species.name().toLowerCase(Locale.ROOT) + "_pen_" + level;
        return service.board()
                .statusOf(id)
                .map(status -> status == BoardStatus.PENDING
                        || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .orElse(false);
    }

    private static Map<String, Integer> materials(String scheme) {
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(scheme);
        if (preset.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> materials = new LinkedHashMap<>();
        preset.orElseThrow().materials().forEach((item, count) -> materials.merge(shortId(item), count, Integer::sum));
        return Map.copyOf(materials);
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

    private static String shortId(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
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
