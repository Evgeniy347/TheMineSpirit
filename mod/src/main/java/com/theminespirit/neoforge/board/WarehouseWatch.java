package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.UpgradePlan;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.board.WarehouseSpot;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WarehouseWatch {
    private static final String STORAGE = "storage";
    private static final String NODE = "warehouse";
    private static final Logger LOGGER = LoggerFactory.getLogger(WarehouseWatch.class);

    private WarehouseWatch() {}

    static boolean handles(String node) {
        return NODE.equals(node) || node.startsWith(NODE + "_");
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        if (post.open(ticket.id())) {
            return true;
        }
        int level = WarehouseRules.levelOf(ticket.node());
        Spot spot = spot(post.level, post.spirits);
        BlockCoord site = WarehouseRules.site(spot.home(), level);
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.BUILD_WAREHOUSE,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        UpgradePlan.required(
                                level > 1 ? WarehouseRules.scheme(level - 1) : null, WarehouseRules.scheme(level))),
                TicketTarget.at(site).withScheme(WarehouseRules.scheme(level)));
        LOGGER.info("{} — задание {}", WarehouseSpot.line(level, spot.home(), site, spot.reason()), ticket.id());
        return true;
    }

    private static Spot spot(ServerLevel level, List<SpiritEntity> spirits) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<Placement> storage = store.town().flatMap(town -> town.placement(STORAGE));
        if (storage.isEmpty()) {
            return new Spot(homeOf(spirits), "в плане поселения нет слота storage: склад у дома духов");
        }
        Optional<StageSite> site = store.site();
        if (site.isEmpty()) {
            return new Spot(homeOf(spirits), "план поселения не сохранён: склад у дома духов");
        }
        Placement place = storage.orElseThrow();
        TownPlan plan = store.town().orElseThrow();
        BlockCoord lot = new BlockCoord(place.x(), site.orElseThrow().anchor().y(), place.z());
        boolean inside = plan.onPlot(place.x(), place.z())
                && plan.onPlot(place.x() + place.width() - 1, place.z() + place.depth() - 1);
        if (!inside) {
            LOGGER.warn(
                    "[warehouse] слот storage {} вне плана поселения: склад ставится у дома духов, "
                            + "проверьте town.json и сохранённый план",
                    lot);
            return new Spot(homeOf(spirits), "слот storage вне плана поселения: склад у дома духов");
        }
        return new Spot(
                lot,
                "слот storage плана, чанк [" + (lot.x() >> 4) + ", " + (lot.z() >> 4) + "] загружен: "
                        + (level.isLoaded(new BlockPos(lot.x(), lot.y(), lot.z())) ? "да" : "нет"));
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        return spirits.stream()
                .flatMap(spirit -> spirit.home().stream())
                .findFirst()
                .orElse(new BlockCoord(0, 64, 0));
    }

    private record Spot(BlockCoord home, String reason) {}
}
