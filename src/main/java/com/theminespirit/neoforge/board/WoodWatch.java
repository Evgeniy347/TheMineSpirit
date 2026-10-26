package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.neoforge.OwnWood;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class WoodWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PLANKS_PER_LOG = 4;
    private static final int CHOPPERS = 2;
    private static final Set<BoardCase> WOODEN =
            EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.REPAIR_BUILDING);
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private WoodWatch() {}

    static void tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            BlockCoord home,
            String fallback,
            long now) {
        MaterialSource source = FirstStepsRunner.materialSource(level);
        Map<String, Integer> needed = new TreeMap<>();
        for (BoardTicket ticket : service.board().all()) {
            if (!WOODEN.contains(ticket.boardCase())
                    || !OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE))) {
                continue;
            }
            TicketTarget target = service.targetOf(ticket.id(), spirits);
            String wood = woodOf(level, ticket, target, fallback);
            needed.merge(wood, planks(level, target, ticket, source, wood), Integer::sum);
        }
        needed.forEach((wood, planks) -> gather(service, source, stock, home, wood, planks, now));
    }

    private static String woodOf(ServerLevel level, BoardTicket ticket, TicketTarget target, String fallback) {
        String own = target.wood();
        if (own != null) {
            return own;
        }
        StageWork work = target.work();
        return work == null ? fallback : BuildingWoodChoice.stored(level, ticket.boardCase(), work.site(), fallback);
    }

    private static void gather(
            BoardService service,
            MaterialSource source,
            StockView stock,
            BlockCoord home,
            String wood,
            int needed,
            long now) {
        int held = OwnWood.planksIn(stock.available(), wood);
        for (Map.Entry<String, Integer> entry : stock.available().entrySet()) {
            boolean made = wood.equals(OwnWood.species(entry.getKey()))
                    && !entry.getKey().endsWith("_planks");
            if (made || OwnWood.stick(entry.getKey())) {
                held += OwnWood.plankCost(entry.getKey(), entry.getValue());
            }
        }
        int deficit = needed - held;
        if (deficit <= 0) {
            return;
        }
        int logs = (deficit + PLANKS_PER_LOG - 1) / PLANKS_PER_LOG;
        LOGGER.info(
                "[wood] стройкам этапа не хватает дерева: нужно {} досок, есть {} — рубка {} брёвен {}",
                needed,
                held,
                logs,
                wood);
        FirstStepsRunner.postGather(service, source, home, wood + "_log", logs, CHOPPERS, now);
    }

    private static int planks(
            ServerLevel level, TicketTarget target, BoardTicket ticket, MaterialSource source, String wood) {
        TaskNode plan;
        try {
            plan = TicketPlans.plan(ticket.boardCase(), ticket.level(), target);
        } catch (RuntimeException unknown) {
            LOGGER.warn("[wood] план {} для подсчёта дерева не собран: {}", ticket.id(), unknown.getMessage());
            return 0;
        }
        Map<String, Integer> need = new HashMap<>(CaseMaterials.need(plan, source));
        FirstStepsRunner.minusPlaced(level, plan, wood, need);
        int planks = 0;
        for (Map.Entry<String, Integer> entry : need.entrySet()) {
            String item = entry.getKey();
            if (item.endsWith(wood + "_log")) {
                planks += entry.getValue() * PLANKS_PER_LOG;
            } else if (wood.equals(OwnWood.species(item)) || OwnWood.stick(item)) {
                planks += OwnWood.plankCost(item, entry.getValue());
            }
        }
        return planks;
    }
}
