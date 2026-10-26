package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class DispatchPick {
    private final DispatchRound round;
    private final Map<UUID, SpiritEntity> entities = new LinkedHashMap<>();
    private final Map<UUID, DispatchRound.Candidate> candidates = new LinkedHashMap<>();

    DispatchPick(DispatchRound round, List<SpiritEntity> spirits) {
        this.round = round;
        for (SpiritEntity spirit : spirits) {
            entities.put(spirit.getUUID(), spirit);
            candidates.put(spirit.getUUID(), candidate(spirit));
        }
    }

    @Nullable
    SpiritEntity choose(BoardTicket ticket, @Nullable TicketTarget target, CrafterMemory crafters) {
        refresh();
        if (ticket.assignee().isPresent()) {
            DispatchRound.Candidate own = candidates.get(ticket.assignee().orElseThrow());
            return own != null && round.fits(ticket, own) ? entities.get(own.id()) : null;
        }
        Optional<UUID> crafter = crafters.remembered(ticket.id());
        if (crafter.filter(round::awaited).isPresent()) {
            return null;
        }
        List<DispatchRound.Candidate> preference = new ArrayList<>(candidates.values());
        preference.sort(Comparator.<DispatchRound.Candidate>comparingInt(
                        spirit -> crafter.filter(spirit.id()::equals).isPresent() ? 0 : 1)
                .thenComparingDouble(spirit -> distance(entities.get(spirit.id()), target))
                .thenComparing(DispatchRound.Candidate::id));
        return round.choose(ticket, preference)
                .map(spirit -> entities.get(spirit.id()))
                .orElse(null);
    }

    String why(BoardTicket ticket) {
        List<DispatchRound.Candidate> all = new ArrayList<>(candidates.values());
        boolean tooled = all.stream().anyMatch(spirit -> ToolGate.allows(ticket.boardCase(), spirit.items()));
        if (!all.isEmpty() && !tooled && ToolGate.required(ticket.boardCase()).isPresent()) {
            return "нет духа с киркой: ждёт кирку (задание " + BoardService.PICKAXE_TICKET + ")";
        }
        return round.why(ticket, all);
    }

    private void refresh() {
        for (SpiritEntity spirit : entities.values()) {
            candidates.put(spirit.getUUID(), candidate(spirit));
        }
    }

    private static double distance(@Nullable SpiritEntity spirit, @Nullable TicketTarget target) {
        if (spirit == null || target == null) {
            return 0;
        }
        return spirit.distanceToSqr(
                target.target().x() + 0.5, target.target().y(), target.target().z() + 0.5);
    }

    static DispatchRound.Candidate candidate(SpiritEntity spirit) {
        RunStatus status = spirit.taskStatus().orElse(null);
        boolean running =
                status == RunStatus.RUNNING || spirit.currentTaskScript().isPresent();
        boolean control = spirit.tactics().wantsControl();
        String busyWith = null;
        if (running && !(spirit.runsBackgroundTask() && !control)) {
            String script = spirit.currentTaskScript().orElse("задача");
            busyWith = control ? "тактика держит (" + script + ")" : script;
        }
        return new DispatchRound.Candidate(
                spirit.getUUID(),
                spirit.displayName(),
                busyWith,
                BoardService.itemIds(spirit),
                new EatNeed(
                        spirit.hunger().foodLevel(), (int) Math.ceil(spirit.getHealth()), SpiritHunger.MAX_FOOD, (int)
                                Math.ceil(spirit.getMaxHealth())),
                new SpiritView(control, false, status == RunStatus.RUNNING));
    }
}
