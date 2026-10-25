package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.intellect.FoodRation;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.NutritionTable;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.SpiritConfig;
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

final class FoodWatch {
    private static final int STATION_RADIUS = 8;
    private static final int STATION_BUDGET = 4096;
    private static final int STATION_SLICES = 16;
    private static final Set<Block> COOKING_STATIONS = Set.of(Blocks.SMOKER, Blocks.FURNACE, Blocks.CAMPFIRE);

    private FoodWatch() {}

    static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        NutritionTable table = StockCatalog.defaultTable();
        IntellectTuning tuning = IntellectTuning.loadDefault();
        double norm = tuning.foodDays() * tuning.foodRation();
        Map<String, Integer> available = qualify(stock.available());
        Boolean station = null;
        for (SpiritEntity spirit : spirits) {
            String id = ticketId(spirit);
            if (!needsRestock(spirit, norm)) {
                cancelWaiting(service, id);
                continue;
            }
            if (station == null) {
                station = cookingStationNear(level, homeCenter(spirits));
            }
            Optional<FoodRation.Choice> choice = FoodRation.choose(available, table, station);
            if (choice.isEmpty() || choice.orElseThrow().needsCooking()) {
                continue;
            }
            String item = choice.orElseThrow().item();
            if (service.board().statusOf(id).isPresent() || service.board().recentlyFailed(id, now)) {
                continue;
            }
            int count = available.getOrDefault(item, 0);
            if (count <= 0) {
                continue;
            }
            service.submit(
                    new BoardTicket(
                            id,
                            BoardCase.RESTOCK_FOOD,
                            Optional.of(spirit.getUUID()),
                            BoardPriorities.effective(BoardCase.RESTOCK_FOOD, PriorityContext.none()),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(targetOf(spirit)).withSupply(item, count));
        }
    }

    private static boolean needsRestock(SpiritEntity spirit, double norm) {
        return FoodRation.due(spirit.hunger().personalNutrition(), norm)
                && spirit.hunger().foodLevel() < SpiritHunger.MAX_FOOD;
    }

    private static void cancelWaiting(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES) {
            service.board().cancel(id);
        }
    }

    private static String ticketId(SpiritEntity spirit) {
        return "food:" + spirit.getUUID();
    }

    private static BlockCoord targetOf(SpiritEntity spirit) {
        Optional<BlockCoord> home = spirit.home();
        return home.orElseGet(() -> new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()));
    }

    private static BlockPos homeCenter(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                BlockCoord coord = home.orElseThrow();
                return new BlockPos(coord.x(), coord.y(), coord.z());
            }
        }
        SpiritEntity first = spirits.get(0);
        return first.blockPosition();
    }

    private static boolean cookingStationNear(ServerLevel level, BlockPos center) {
        BlockLocator.Search search = BlockLocator.search(level, center, STATION_RADIUS, COOKING_STATIONS);
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
