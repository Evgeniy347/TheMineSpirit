package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.building.api.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StagePlacePost {
    private StagePlacePost() {}

    static boolean handles(String node) {
        return StageCatalog.placed(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        StageCatalog.Entry entry = StageCatalog.entry(ticket.node())
                .orElseThrow(() -> new IllegalStateException("Постройка " + ticket.node()
                        + " без кейса и схемы: задание постройки этапа не собрать без места и схемы"));
        if (post.open(ticket.id())) {
            return true;
        }
        Optional<BlockCoord> at = SettlementPlotStore.get(post.level)
                .town()
                .flatMap(town -> StageCatalog.origin(
                        ticket.node(), town, post.scan.stage().anchor().y()));
        if (at.isEmpty()) {
            return false;
        }
        TicketTarget target =
                TicketTarget.at(at.orElseThrow()).withScheme(entry.scheme()).withBuilding(ticket.node());
        target = level(target, post, ticket.node());
        String wood = OrchardSites.speciesOfBuilding(ticket.node()).orElse(post.scan.wood());
        if (!WoodSpecies.OAK.equals(wood)) {
            target = target.withWood(wood);
        }
        List<String> modifiers = tools(target, entry.scheme());
        post.submit(
                post.ticket(
                        ticket.id(),
                        entry.boardCase(),
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of(),
                        modifiers),
                target);
        return true;
    }

    static TicketTarget level(TicketTarget target, GraphPost post, String node) {
        return SettlementPlotStore.get(post.level)
                .town()
                .flatMap(town -> town.placement(node))
                .map(place -> {
                    int levelY = post.scan.stage().anchor().y();
                    PlotLeveling plan = PlotLeveling.plan(
                            new BlockCoord(place.x(), levelY, place.z()),
                            place.mask(),
                            SettlementGround.tops(post.level, levelY));
                    return target.withLeveling(plan);
                })
                .orElse(target);
    }

    private static List<String> tools(TicketTarget target, String scheme) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(scheme)
                .map(preset -> {
                    boolean leveling = target.leveling() != null
                            && (!target.leveling().cut().isEmpty()
                                    || !target.leveling().fill().isEmpty());
                    List<String> base = new ArrayList<>();
                    if (SchematicGround.tilled(preset.schematic())) {
                        base.add("hoe");
                    }
                    if (SchematicGround.walkway(preset.schematic())) {
                        base.add("shovel");
                    }
                    List<String> required = requiredTools(base, leveling);
                    return required.isEmpty()
                            ? List.<String>of()
                            : List.of(ToolGate.TOOLS_MODIFIER + String.join(",", required));
                })
                .orElseGet(List::of);
    }

    static List<String> requiredTools(List<String> base, boolean leveling) {
        List<String> required = new ArrayList<>(base);
        if (leveling) {
            required.add(ToolGate.PICKAXE);
        }
        return List.copyOf(required);
    }
}
