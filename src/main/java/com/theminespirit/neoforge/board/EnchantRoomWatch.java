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
 * Источник доски «чаровальня»: уровень 1 — деревянная изба со столом и пятью полками, уровень 2 — каменный
 * зал на десять полок, уровень 3 — большой зал на пятнадцать полок (максимум стола).
 * Уровень не прыгает: следующий ставится после выполненного предыдущего и ждёт свои полки гейтом.
 * Постоянные запасы (полки, книги, бумага) держит {@link BoardService}, а не этот источник.
 */
public final class EnchantRoomWatch {
    private static final int LEVEL_ONE_LAPIS = 4;
    private static final int LEVEL_ONE_BOOK = 1;
    private static final int LEVEL_TWO_SHELVES = 10;
    private static final int LEVEL_THREE_SHELVES = 15;
    private static final int LAST_LEVEL = 3;

    private EnchantRoomWatch() {}

    public static void tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty() || active(service)) {
            return;
        }
        int max = Math.min(LAST_LEVEL, BuildingLevels.maxLevel(BoardCase.ENCHANT_ROOM));
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
        }
        int shelves = switch (next) {
            case 1 -> 0;
            case 2 -> LEVEL_TWO_SHELVES;
            case 3 -> LEVEL_THREE_SHELVES;
            default ->
                throw new IllegalStateException("У чаровальни нет уровня " + next + ": доступны 1–" + LAST_LEVEL);
        };
        Map<String, Integer> required = next == 1
                ? Map.of("lapis_lazuli", LEVEL_ONE_LAPIS, "book", LEVEL_ONE_BOOK)
                : Map.of("bookshelf", shelves);
        service.submit(
                new BoardTicket(
                        "enchant_room_" + next,
                        BoardCase.ENCHANT_ROOM,
                        Optional.empty(),
                        BoardPriorities.effective(BoardCase.ENCHANT_ROOM, PriorityContext.none()),
                        now,
                        next,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        required),
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
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
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
