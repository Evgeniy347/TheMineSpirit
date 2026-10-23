package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.TorchRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * Ставит путь «уголь → факел» на доску ({@link TorchRules}): одно задание факелов за раз, приоритет — таблица доски.
 * Древесный уголь — духу, у которого печь в инвентаре, если печь ещё не стоит у дома: он её и поставит при плавке.
 */
public final class TorchWatch {
    public static final String PREFIX = "torch:";
    private static final Logger LOGGER = LogUtils.getLogger();

    private TorchWatch() {}

    public static void tick(
            BoardService service,
            List<SpiritEntity> spirits,
            BlockCoord home,
            StockView stock,
            boolean furnaceBlock,
            long now) {
        if (active(service)) {
            return;
        }
        Optional<UUID> furnaceHolder = furnaceBlock ? Optional.empty() : furnaceHolder(spirits);
        boolean furnace = furnaceBlock || furnaceHolder.isPresent();
        Optional<TorchRules.TorchStep> next = TorchRules.next(stock.available(), furnace);
        if (next.isEmpty()) {
            return;
        }
        TorchRules.TorchStep step = next.orElseThrow();
        TicketTarget target;
        Map<String, Integer> required;
        Optional<UUID> assignee;
        if (step.boardCase() == BoardCase.CRAFT_TORCHES) {
            target = TicketTarget.at(home).withCount(step.count());
            required = Map.of(step.item(), step.count() / TorchRules.TORCHES_PER_CRAFT);
            assignee = Optional.empty();
        } else {
            target = TicketTarget.at(home)
                    .withSupply(step.item(), TorchRules.CHARCOAL_LOGS)
                    .withCount(step.count());
            required = Map.of(step.item(), TorchRules.CHARCOAL_LOGS);
            assignee = furnaceHolder;
        }
        String id = ticketId(step.boardCase());
        LOGGER.info("Поставлено: {} {} ×{} (путь «уголь → факел»)", step.boardCase(), step.item(), step.count());
        service.submit(
                new BoardTicket(
                        id,
                        step.boardCase(),
                        assignee,
                        BoardRules.priorityOf(step.boardCase()),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        required),
                target);
    }

    public static String ticketId(BoardCase boardCase) {
        return PREFIX + boardCase.name().toLowerCase(Locale.ROOT);
    }

    private static boolean active(BoardService service) {
        for (BoardCase boardCase : List.of(BoardCase.CRAFT_TORCHES, BoardCase.SMELT_CHARCOAL)) {
            BoardStatus status = service.board().statusOf(ticketId(boardCase)).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                return true;
            }
        }
        return false;
    }

    private static Optional<UUID> furnaceHolder(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            if (!spirit.isRemoved() && spirit.getInventory().countItem(Items.FURNACE) > 0) {
                return Optional.of(spirit.getUUID());
            }
        }
        return Optional.empty();
    }
}
