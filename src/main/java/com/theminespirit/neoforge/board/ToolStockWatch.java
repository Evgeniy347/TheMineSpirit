package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class ToolStockWatch {
    public static final String PREFIX = "stock:";
    private static final Logger LOGGER = LogUtils.getLogger();

    private ToolStockWatch() {}

    public static void tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        MaterialSource source = null;
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isEmpty() || spirit.isRemoved()) {
                continue;
            }
            Map<BoardCase, Integer> shortages = ToolSet.woodenShortages(ownTools(spirit), ToolSet.count(1));
            for (Map.Entry<BoardCase, Integer> entry : shortages.entrySet()) {
                BoardCase caseValue = entry.getKey();
                String id = ticketId(caseValue, spirit.getUUID());
                if (!service.board().ledger().has(caseValue, 1) || active(service, id)) {
                    continue;
                }
                TicketTarget target = TicketTarget.at(home.orElseThrow()).withToolCount(1);
                if (source == null) {
                    source = FirstStepsRunner.materialSource(level);
                }
                Map<String, Integer> missing = ResourceGate.missing(
                        new StockNeed(FirstStepsRunner.materials(source, caseValue, target)), stock);
                if (!missing.isEmpty()) {
                    LOGGER.debug(
                            "Запас инструмента {} духа {} ждёт материалов: {}",
                            caseValue,
                            spirit.displayName(),
                            missing);
                    continue;
                }
                LOGGER.info(
                        "Поставлено: восполнить {} духу {} (до его набора не хватает {})",
                        caseValue,
                        spirit.displayName(),
                        entry.getValue());
                service.submit(
                        new BoardTicket(
                                id,
                                caseValue,
                                Optional.of(spirit.getUUID()),
                                BoardRules.priorityOf(caseValue),
                                now,
                                1,
                                BoardTicket.ALWAYS_CAUSE,
                                List.of(),
                                Map.of()),
                        target);
            }
        }
    }

    private static Map<String, Integer> ownTools(SpiritEntity spirit) {
        Map<String, Integer> own = new HashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            addTool(own, spirit.getInventory().getItem(slot));
        }
        if (spirit.mainHandOwned()) {
            addTool(own, spirit.getMainHandItem());
        }
        return own;
    }

    private static void addTool(Map<String, Integer> own, ItemStack stack) {
        if (!stack.isEmpty()) {
            own.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
        }
    }

    public static String ticketId(BoardCase caseValue, UUID spirit) {
        return PREFIX + caseValue.name().toLowerCase(Locale.ROOT) + ":" + spirit;
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }
}
