package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.DigOutScript;
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
    private final List<String> stockTools;

    DispatchPick(DispatchRound round, List<SpiritEntity> spirits, StockView stock) {
        this.round = round;
        this.stockTools = stockTools(stock);
        for (SpiritEntity spirit : spirits) {
            entities.put(spirit.getUUID(), spirit);
            candidates.put(spirit.getUUID(), candidate(spirit, stockTools));
            round.board()
                    .stuck()
                    .see(
                            spirit.getUUID(),
                            at(spirit),
                            round.board().now(),
                            DigOutScript.open(spirit.level(), spirit.blockPosition()));
        }
    }

    static List<String> stockTools(StockView stock) {
        return stock.available().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static BlockCoord at(SpiritEntity spirit) {
        return new BlockCoord(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
    }

    @Nullable
    SpiritEntity choose(BoardTicket ticket, @Nullable TicketTarget target, CrafterMemory crafters) {
        refresh();
        String supply = target == null ? null : target.supplyItem();
        if (ticket.assignee().isPresent()) {
            DispatchRound.Candidate own = candidates.get(ticket.assignee().orElseThrow());
            if (own != null && round.fits(ticket, own, supply)) {
                return entities.get(own.id());
            }
            boolean stuck = own != null
                    && round.board().stuck().isStuck(own.id(), round.board().now());
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM || !stuck) {
                return null;
            }
        }
        Optional<UUID> crafter = crafters.remembered(ticket.id());
        if (crafter.filter(round::awaited).isPresent()) {
            return null;
        }
        List<DispatchRound.Candidate> preference = new ArrayList<>(candidates.values());
        if (target != null) {
            preference.removeIf(spirit -> DispatchRound.tooLowForCleanup(
                    ticket.boardCase(),
                    (int) Math.floor(entities.get(spirit.id()).getY()),
                    target.target().y()));
        }
        preference.sort(Comparator.<DispatchRound.Candidate>comparingInt(
                        spirit -> crafter.filter(spirit.id()::equals).isPresent() ? 0 : 1)
                .thenComparingDouble(spirit -> distance(entities.get(spirit.id()), target))
                .thenComparing(DispatchRound.Candidate::id));
        return round.choose(ticket, preference, supply)
                .map(spirit -> entities.get(spirit.id()))
                .orElse(null);
    }

    String whyChosen(BoardTicket ticket, SpiritEntity spirit, CrafterMemory crafters) {
        if (ticket.assignee().filter(spirit.getUUID()::equals).isPresent()) {
            return spirit.displayName() + " — назначенный держатель задания";
        }
        if (crafters.remembered(ticket.id()).filter(spirit.getUUID()::equals).isPresent()) {
            return spirit.displayName() + " — крафтер задания";
        }
        return spirit.displayName() + " — ближайший достижимый дух";
    }

    String why(BoardTicket ticket, @Nullable TicketTarget target) {
        String supply = target == null ? null : target.supplyItem();
        return round.why(ticket, new ArrayList<>(candidates.values()), supply);
    }

    private void refresh() {
        for (SpiritEntity spirit : entities.values()) {
            candidates.put(spirit.getUUID(), candidate(spirit, stockTools));
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
        return candidate(spirit, List.of());
    }

    static DispatchRound.Candidate candidate(SpiritEntity spirit, List<String> stockTools) {
        RunStatus status = spirit.taskStatus().orElse(null);
        boolean running =
                status == RunStatus.RUNNING || spirit.currentTaskScript().isPresent();
        boolean control = spirit.tactics().wantsControl();
        DispatchRound.BusyMode mode = !running
                ? DispatchRound.BusyMode.IDLE
                : control
                        ? DispatchRound.BusyMode.TACTICS
                        : spirit.runsBackgroundTask() ? DispatchRound.BusyMode.BACKGROUND : DispatchRound.BusyMode.WORK;
        String busyWith = DispatchRound.busyWith(
                spirit.currentTaskScript().orElse(null), spirit.currentPlan().orElse(null), mode);
        return new DispatchRound.Candidate(
                spirit.getUUID(),
                spirit.displayName(),
                busyWith,
                SpiritItems.ids(spirit),
                new EatNeed(
                        spirit.hunger().foodLevel(), (int) Math.ceil(spirit.getHealth()), SpiritHunger.MAX_FOOD, (int)
                                Math.ceil(spirit.getMaxHealth())),
                new SpiritView(control, false, status == RunStatus.RUNNING),
                spirit.isInWater(),
                stockTools);
    }
}
