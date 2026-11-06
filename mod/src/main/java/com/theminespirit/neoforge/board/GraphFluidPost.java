package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.FarmWaterCell;
import com.theminespirit.core.board.FluidPlans;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.FluidKind;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

final class GraphFluidPost {
    private static final Set<String> KEYS = Set.of("more6_fetch_water", "more6_fetch_lava", "more5_water_crops");
    private static final String BUCKET = "bucket";

    private GraphFluidPost() {}

    static boolean handles(String key) {
        return KEYS.contains(key);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, String key) {
        return switch (key) {
            case "more6_fetch_water" -> fetch(post, ticket, FluidKind.WATER);
            case "more6_fetch_lava" -> fetch(post, ticket, FluidKind.LAVA);
            case "more5_water_crops" -> waterCrops(post, ticket);
            default -> false;
        };
    }

    private static boolean fetch(GraphPost post, GraphTickets.Ticket ticket, FluidKind fluid) {
        Optional<BlockCoord> source = FluidSources.nearest(post.level, post.scan.home(), fluid);
        if (source.isEmpty()) {
            return false;
        }
        Optional<BlockCoord> cell = fluid == FluidKind.WATER ? waterCell(post) : Optional.empty();
        BlockCoord destination = cell.orElseGet(post.scan::home);
        GraphMorePost.submit(
                post,
                ticket,
                cell.isPresent() ? BoardCase.ACTIVITY_FARM : BoardCase.ACTIVITY_UNLOAD,
                TicketTarget.at(destination).withPlan(FluidPlans.fetch(fluid, source.orElseThrow(), destination)),
                Map.of(BUCKET, 1));
        return true;
    }

    private static boolean waterCrops(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<BlockCoord> cell = waterCell(post);
        Optional<BlockCoord> source = FluidSources.nearest(post.level, post.scan.home(), FluidKind.WATER);
        if (cell.isEmpty() || source.isEmpty()) {
            return false;
        }
        GraphMorePost.submit(
                post,
                ticket,
                BoardCase.ACTIVITY_FARM,
                TicketTarget.at(cell.orElseThrow())
                        .withPlan(FluidPlans.waterCrops(FluidKind.WATER, source.orElseThrow(), cell.orElseThrow())),
                Map.of(BUCKET, 1));
        return true;
    }

    private static Optional<BlockCoord> waterCell(GraphPost post) {
        BlockCoord home = post.scan.home();
        return post.facts.buildings().entrySet().stream()
                .filter(entry -> FarmWaterCell.field(entry.getKey()))
                .filter(entry -> post.state.built(entry.getKey()) == GraphState.Built.BUILT)
                .filter(entry -> entry.getValue().position() != null)
                .sorted(Comparator.comparingLong(
                        entry -> distance(home, entry.getValue().position())))
                .map(entry -> dryCell(post, entry.getKey(), entry.getValue().position()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<BlockCoord> dryCell(GraphPost post, String building, GraphFacts.Position position) {
        Optional<StageCatalog.Entry> entry = StageCatalog.entry(building);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        Optional<Schematic> scheme = DataTables.INSTANCE
                .presets()
                .current()
                .byId(entry.orElseThrow().scheme())
                .map(Preset::schematic);
        if (scheme.isEmpty()) {
            return Optional.empty();
        }
        BlockCoord origin = new BlockCoord(position.x(), position.y(), position.z());
        Optional<BlockCoord> cell = FarmWaterCell.plannedAll(scheme.orElseThrow(), origin).stream()
                .filter(at -> {
                    BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
                    return post.level.isLoaded(pos)
                            && post.level.getBlockState(pos).isAir();
                })
                .findFirst();
        if (cell.isEmpty()) {
            return Optional.empty();
        }
        boolean dry = More6Scan.dryFields(post.level, List.of(position)) > 0;
        return dry ? cell : Optional.empty();
    }

    private static long distance(BlockCoord home, GraphFacts.Position position) {
        long dx = (long) home.x() - position.x();
        long dy = (long) home.y() - position.y();
        long dz = (long) home.z() - position.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
