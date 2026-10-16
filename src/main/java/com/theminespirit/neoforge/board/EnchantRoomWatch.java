package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
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
 * Источник доски «чаровальня»: при наличии ресурсов на стол ставит {@code ENCHANT_ROOM},
 * после постройки при нехватке книг — {@code ENCHANT_SHELVES}.
 */
public final class EnchantRoomWatch {
    private EnchantRoomWatch() {
    }

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits,
            StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        BlockCoord home = homeOf(spirits);
        if (!hasActive(service, BoardCase.ENCHANT_ROOM) && !hasActive(service, BoardCase.ENCHANT_SHELVES)) {
            int lapis = stock.available().getOrDefault("minecraft:lapis_lazuli", 0);
            int book = stock.available().getOrDefault("minecraft:book", 0);
            if (lapis >= 4 && book >= 1) {
                service.submit(new BoardTicket("enchant_room", BoardCase.ENCHANT_ROOM, Optional.empty(),
                        BoardRules.priorityOf(BoardCase.ENCHANT_ROOM), now, 1, BoardTicket.ALWAYS_CAUSE,
                        List.of(), Map.of()), TicketTarget.at(home));
                return;
            }
        }
        if (!hasActive(service, BoardCase.ENCHANT_ROOM)) {
            return;
        }
        int shelvesBooks = stock.available().getOrDefault("minecraft:bookshelf", 0) * 3
                + stock.available().getOrDefault("minecraft:book", 0);
        if (shelvesBooks < BoardTuning.enchantShelvesMin()) {
            service.submit(new BoardTicket("enchant_shelves", BoardCase.ENCHANT_SHELVES, Optional.empty(),
                    BoardRules.priorityOf(BoardCase.ENCHANT_SHELVES), now, 1, BoardTicket.ALWAYS_CAUSE,
                    List.of(), Map.of("minecraft:book", 16)), TicketTarget.at(home));
        }
    }

    private static boolean hasActive(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(t -> t.boardCase() == boardCase)
                .anyMatch(t -> service.board().statusOf(t.id())
                        .map(s -> s == BoardStatus.PENDING || s == BoardStatus.WAITING_RESOURCES
                                || s == BoardStatus.ASSIGNED)
                        .orElse(false));
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
