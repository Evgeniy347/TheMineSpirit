package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Applicability;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/**
 * Постоянное задание «поесть до полной сытости»: адресуется духу, которому не хватает
 * сытости или здоровья, и снимается с очереди, когда нужда отпала. Задание остаётся
 * на доске выполненным — оно видно как условие, а не исчезает.
 */
final class EvergreenWatch {
    private EvergreenWatch() {
    }

    static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        for (SpiritEntity spirit : spirits) {
            String id = ticketId(spirit);
            EatNeed eat = eatNeed(spirit);
            SpiritView view = viewOf(spirit);
            if (needsEat(spirit, eat, view)) {
                service.submit(new BoardTicket(id, BoardCase.EAT_TO_FULL,
                        Optional.of(spirit.getUUID()),
                        BoardPriorities.effective(BoardCase.EAT_TO_FULL, PriorityContext.none()),
                        now, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()));
                continue;
            }
            cancelWaiting(service, id);
        }
    }

    private static boolean needsEat(SpiritEntity spirit, EatNeed eat, SpiritView view) {
        return spirit.hunger().hasFood() && !eat.full()
                && Applicability.applicable(BoardCase.EAT_TO_FULL, eat, view);
    }

    private static void cancelWaiting(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES) {
            service.board().cancel(id);
        }
    }

    private static EatNeed eatNeed(SpiritEntity spirit) {
        return new EatNeed(spirit.hunger().foodLevel(), (int) Math.ceil(spirit.getHealth()),
                SpiritHunger.MAX_FOOD, (int) Math.ceil(spirit.getMaxHealth()));
    }

    private static SpiritView viewOf(SpiritEntity spirit) {
        return new SpiritView(spirit.tactics().wantsControl(), false,
                spirit.taskStatus().orElse(null) == RunStatus.RUNNING);
    }

    static String ticketId(SpiritEntity spirit) {
        return "eat:" + spirit.getUUID();
    }
}
