package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.fishing.FishingNeeds;
import com.theminespirit.core.fishing.FishingSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.FishingWorkPointPicker;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

/**
 * Два постоянных задания рыболовства: коптильня под накопленный улов ({@code fish:smoker})
 * и рыбалка на запомненной рабочей точке у воды ({@code fish:work}). Мир читает только этот
 * адаптер; решения о выдаче — чистые функции {@link FishingNeeds}, место выбирает
 * {@link FishingWorkPointPicker}. Число-порог рыбы и размер рабочей точки — из fishing.json.
 */
final class FishingWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int STATION_RADIUS = 8;
    private static final int STATION_BUDGET = 4096;
    private static final int STATION_SLICES = 16;
    private static final int WORK_POINT_RADIUS = 32;
    private static final double WORK_POINT_AVOID = 3.0;
    private static final String SMOKER_ID = "fish:smoker";
    private static final String WORK_ID = "fish:work";
    private static final Set<Block> SMOKERS = Set.of(Blocks.SMOKER);

    private final Map<String, FishingSpots.Spot> workPoints = new HashMap<>();

    void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        BlockPos home = homeCenter(spirits);
        if (home == null) {
            return;
        }
        FishingData data = FishingData.loadDefault();
        tickSmoker(level, service, stock, now, home, data);
        tickWork(level, service, spirits, stock, now, home, data);
    }

    private void tickSmoker(
            ServerLevel level, BoardService service, StockView stock, long now, BlockPos home, FishingData data) {
        if (!FishingNeeds.wantsSmoker(stock.available(), data) || smokerNear(level, home)) {
            cancelWaiting(service, SMOKER_ID);
            return;
        }
        if (active(service, SMOKER_ID)) {
            return;
        }
        BlockCoord at = new BlockCoord(home.getX(), home.getY(), home.getZ());
        TicketTarget target = TicketTarget.at(at);
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = materials(source, target);
        LOGGER.info(
                "Поставлено: коптильня под улов — рыбы {} при пороге {}",
                FishingNeeds.cookableFish(stock.available()),
                data.smokerFish());
        service.submit(
                new BoardTicket(
                        SMOKER_ID,
                        BoardCase.CRAFT_SMOKER,
                        Optional.empty(),
                        BoardPriorities.effective(BoardCase.CRAFT_SMOKER, PriorityContext.none()),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        need),
                target);
    }

    private void tickWork(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            long now,
            BlockPos home,
            FishingData data) {
        double share = FishingNeeds.foodShare(stock.available(), spirits.size());
        if (!FishingNeeds.wantsFishing(share)) {
            cancelWaiting(service, WORK_ID);
            return;
        }
        FishingSpots.Spot spot = workPoint(level, home, data);
        if (spot == null) {
            return;
        }
        if (active(service, WORK_ID)) {
            return;
        }
        LOGGER.info("Поставлено: рыбалка на рабочей точке {} (нехватка еды {})", spot.stand(), share);
        service.submit(
                new BoardTicket(
                        WORK_ID,
                        BoardCase.FISH,
                        Optional.empty(),
                        BoardPriorities.effective(BoardCase.FISH, PriorityContext.none()),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(spot.stand()));
    }

    private FishingSpots.Spot workPoint(ServerLevel level, BlockPos home, FishingData data) {
        String key = home.getX() + "," + home.getY() + "," + home.getZ();
        FishingSpots.Spot remembered = workPoints.get(key);
        if (remembered != null) {
            return remembered;
        }
        BlockCoord origin = new BlockCoord(home.getX(), home.getY(), home.getZ());
        Optional<FishingSpots.Spot> found =
                FishingWorkPointPicker.pick(level, origin, data, WORK_POINT_RADIUS, List.of(), WORK_POINT_AVOID);
        found.ifPresent(spot -> workPoints.put(key, spot));
        return found.orElse(null);
    }

    private static Map<String, Integer> materials(MaterialSource source, TicketTarget target) {
        try {
            TaskNode plan = TicketPlans.plan(BoardCase.CRAFT_SMOKER, 1, target);
            return CaseMaterials.need(plan, source);
        } catch (RuntimeException failure) {
            LOGGER.error("Материалы коптильни не посчитаны: {}", failure.getMessage());
            return Map.of();
        }
    }

    private static MaterialSource materialSource(ServerLevel level) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(level);
        return new MaterialSource() {
            @Override
            public int outputCount(String item) {
                return knowledge.outputCount(item);
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return knowledge.ingredientsFor(item);
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                return Map.of();
            }
        };
    }

    private static boolean smokerNear(ServerLevel level, BlockPos center) {
        BlockLocator.Search search = BlockLocator.search(level, center, STATION_RADIUS, SMOKERS);
        while (!search.done() && search.examinedPositions() < STATION_BUDGET) {
            if (!search.step(STATION_SLICES).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos homeCenter(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                BlockCoord coord = home.orElseThrow();
                return new BlockPos(coord.x(), coord.y(), coord.z());
            }
        }
        return null;
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static void cancelWaiting(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES) {
            service.board().cancel(id);
        }
    }
}
