package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/**
 * Источник доски «чаровальня»: уровень 1 — здание со столом, уровень 2 — кольцо полок.
 * Уровень не прыгает: второй ставится после выполненного первого и ждёт полки гейтом.
 * Постоянные запасы (полки, книги, бумага) держит {@link BoardService}, а не этот источник.
 */
public final class EnchantRoomWatch {
    private static final int LEVEL_ONE_LAPIS = 4;
    private static final int LEVEL_ONE_BOOK = 1;
    private static final int LEVEL_TWO_SHELVES = 15;

    private EnchantRoomWatch() {
    }

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits,
            StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty() || active(service)) {
            return;
        }
        int max = Math.min(2, BuildingLevels.maxLevel(BoardCase.ENCHANT_ROOM));
        int next = doneLevel(service) + 1;
        if (next > max) {
            return;
        }
        BlockCoord home = homeOf(spirits);
        if (next == 1) {
            int lapis = stock.available().getOrDefault("lapis_lazuli", 0);
            int book = stock.available().getOrDefault("book", 0);
            if (lapis < LEVEL_ONE_LAPIS || book < LEVEL_ONE_BOOK) {
                return;
            }
            service.submit(new BoardTicket("enchant_room_1", BoardCase.ENCHANT_ROOM, Optional.empty(),
                    BoardPriorities.effective(BoardCase.ENCHANT_ROOM, PriorityContext.none()), now, 1,
                    BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()), TicketTarget.at(home));
            return;
        }
        service.submit(new BoardTicket("enchant_room_2", BoardCase.ENCHANT_ROOM, Optional.empty(),
                BoardPriorities.effective(BoardCase.ENCHANT_ROOM, PriorityContext.none()), now, 2,
                BoardTicket.ALWAYS_CAUSE, List.of(), Map.of("bookshelf", LEVEL_TWO_SHELVES)),
                TicketTarget.at(home));
    }

    private static int doneLevel(BoardService service) {
        int done = 0;
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != BoardCase.ENCHANT_ROOM
                    || service.board().statusOf(ticket.id()).orElse(null) != BoardStatus.DONE) {
                continue;
            }
            done = Math.max(done, ticket.level());
        }
        return done;
    }

    private static boolean active(BoardService service) {
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != BoardCase.ENCHANT_ROOM) {
                continue;
            }
            BoardStatus status = service.board().statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                return true;
            }
        }
        return false;
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
