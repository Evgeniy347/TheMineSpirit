package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.intellect.FoodRation;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.NutritionTable;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class GraphFood {
    private static final int STATION_RADIUS = 8;
    private static final int STATION_BUDGET = 4096;
    private static final int STATION_SLICES = 16;
    private static final Set<Block> COOKING_STATIONS = Set.of(Blocks.SMOKER, Blocks.FURNACE, Blocks.CAMPFIRE);

    private GraphFood() {}

    static boolean hungry(List<SpiritEntity> spirits) {
        double norm = norm();
        return spirits.stream().anyMatch(spirit -> needsRestock(spirit, norm));
    }

    static void post(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            String id,
            int priority,
            long now) {
        double norm = norm();
        NutritionTable table = StockCatalog.defaultTable();
        Map<String, Integer> available = qualify(stock.available());
        Optional<Boolean> station = Optional.empty();
        for (SpiritEntity spirit : spirits) {
            if (!needsRestock(spirit, norm)) {
                continue;
            }
            if (station.isEmpty()) {
                station = Optional.of(cookingStationNear(level, targetOf(spirit)));
            }
            Optional<FoodRation.Choice> choice = FoodRation.choose(available, table, station.orElseThrow());
            if (choice.isEmpty() || choice.orElseThrow().needsCooking()) {
                continue;
            }
            String item = choice.orElseThrow().item();
            int count = available.getOrDefault(item, 0);
            String ticketId = id + "@" + spirit.getUUID();
            BoardOutcome outcome = service.submit(
                    new BoardTicket(
                            ticketId,
                            BoardCase.RESTOCK_FOOD,
                            Optional.of(spirit.getUUID()),
                            priority,
                            now,
                            1,
                            id,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(targetOf(spirit)).withSupply(item, count));
            if (outcome == BoardOutcome.DUPLICATE) {
                service.board().reprioritize(ticketId, priority);
            }
        }
    }

    private static double norm() {
        IntellectTuning tuning = IntellectTuning.loadDefault();
        return tuning.foodDays() * tuning.foodRation();
    }

    private static boolean needsRestock(SpiritEntity spirit, double norm) {
        return FoodRation.due(spirit.hunger().personalNutrition(), norm)
                && spirit.hunger().foodLevel() < SpiritHunger.MAX_FOOD;
    }

    private static BlockCoord targetOf(SpiritEntity spirit) {
        Optional<BlockCoord> home = spirit.home();
        return home.orElseGet(() -> new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()));
    }

    private static boolean cookingStationNear(ServerLevel level, BlockCoord center) {
        BlockLocator.Search search = BlockLocator.search(
                level, new BlockPos(center.x(), center.y(), center.z()), STATION_RADIUS, COOKING_STATIONS);
        while (!search.done() && search.examinedPositions() < STATION_BUDGET) {
            if (!search.step(STATION_SLICES).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> qualify(Map<String, Integer> stock) {
        Map<String, Integer> qualified = new HashMap<>();
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            String id = entry.getKey().indexOf(':') < 0 ? "minecraft:" + entry.getKey() : entry.getKey();
            qualified.merge(id, entry.getValue(), Integer::sum);
        }
        return qualified;
    }
}
