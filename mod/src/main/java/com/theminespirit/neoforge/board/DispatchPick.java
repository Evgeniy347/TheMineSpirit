package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchBusy;
import com.theminespirit.core.board.DispatchReach;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.StandSearch;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class DispatchPick {
    private final DispatchRound round;
    private final Map<UUID, SpiritEntity> entities = new LinkedHashMap<>();
    private final Map<UUID, DispatchRound.Candidate> candidates = new LinkedHashMap<>();
    private final Map<TicketTarget, BlockCoord> approachTargets = new HashMap<>();
    private final List<String> stockTools;
    private final DispatchReach.@Nullable Cache reach;

    DispatchPick(DispatchRound round, List<SpiritEntity> spirits, StockView stock) {
        this(round, spirits, stock, null);
    }

    DispatchPick(
            DispatchRound round, List<SpiritEntity> spirits, StockView stock, DispatchReach.@Nullable Cache reach) {
        this.round = round;
        this.stockTools = stockTools(stock);
        this.reach = reach;
        for (SpiritEntity spirit : spirits) {
            entities.put(spirit.getUUID(), spirit);
            candidates.put(spirit.getUUID(), candidate(spirit, stockTools));
            round.board()
                    .stuck()
                    .see(spirit.getUUID(), at(spirit), round.board().now(), PitRescue.escaped(spirit));
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
        return pick(ticket, target, crafters);
    }

    void refresh(SpiritEntity spirit) {
        candidates.put(spirit.getUUID(), candidate(spirit, stockTools));
    }

    @Nullable
    private SpiritEntity pick(BoardTicket ticket, @Nullable TicketTarget target, CrafterMemory crafters) {
        String supply = target == null ? null : target.supplyItem();
        if (ticket.assignee().isPresent()) {
            DispatchRound.Candidate own = candidates.get(ticket.assignee().orElseThrow());
            if (own != null && reachable(own, target)) {
                Optional<DispatchRound.Candidate> chosen = round.choose(ticket, List.of(own), supply);
                if (chosen.isPresent()) {
                    return entities.get(chosen.orElseThrow().id());
                }
            }
            boolean stuck = own != null
                    && round.board().stuck().isStuck(own.id(), round.board().now());
            if (!stuck) {
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
        preference.removeIf(spirit -> !reachable(spirit, target));
        preference.sort(Comparator.<DispatchRound.Candidate>comparingInt(
                        spirit -> crafter.filter(spirit.id()::equals).isPresent() ? 0 : 1)
                .thenComparingDouble(spirit -> distance(entities.get(spirit.id()), target))
                .thenComparing(DispatchRound.Candidate::id));
        return round.choose(ticket, preference, supply)
                .map(spirit -> entities.get(spirit.id()))
                .orElse(null);
    }

    private boolean reachable(DispatchRound.Candidate spirit, @Nullable TicketTarget target) {
        if (reach == null || target == null) {
            return true;
        }
        SpiritEntity entity =
                Objects.requireNonNull(entities.get(spirit.id()), "Кандидат выдачи потерял сущность духа");
        BlockCoord from = at(entity);
        return DispatchReach.allows(reach, spirit.id(), from, approachTarget(target));
    }

    private BlockCoord approachTarget(TicketTarget target) {
        return approachTargets.computeIfAbsent(target, this::standNear);
    }

    private BlockCoord standNear(TicketTarget target) {
        BlockCoord point = reachTarget(target);
        if (target.leveling() == null || entities.isEmpty()) {
            return point;
        }
        BlockCoord top = new BlockCoord(point.x(), reachHeight(target), point.z());
        LevelSafetyView view =
                new LevelSafetyView(entities.values().iterator().next().level());
        return StandSearch.nearest(top, cell -> MiningSafety.canStand(cell, view));
    }

    static int reachHeight(TicketTarget target) {
        BlockCoord point = reachTarget(target);
        if (target.leveling() == null) {
            return point.y();
        }
        return target.leveling().cut().stream()
                .filter(box -> point.x() >= box.x1()
                        && point.x() <= box.x2()
                        && point.z() >= box.z1()
                        && point.z() <= box.z2())
                .mapToInt(PlotLeveling.Box::y2)
                .max()
                .orElse(point.y());
    }

    static BlockCoord reachTarget(TicketTarget target) {
        BlockCoord origin = target.target();
        if (target.schemeFile() == null || target.leveling() != null) {
            return origin;
        }
        return PresetLibrary.current()
                .byId(target.schemeFile())
                .map(preset -> new BlockCoord(
                        origin.x() + preset.entry().x(),
                        origin.y() + preset.entry().y(),
                        origin.z() + preset.entry().z()))
                .orElse(origin);
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
        List<DispatchRound.Candidate> spirits = new ArrayList<>(candidates.values());
        String why = round.why(ticket, spirits, supply);
        if (reach != null && target != null && spirits.stream().noneMatch(spirit -> reachable(spirit, target))) {
            return why + "; путь до цели не найден или ещё проверяется";
        }
        return why;
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
        DispatchBusy.Mode mode = !running
                ? DispatchBusy.Mode.IDLE
                : control
                        ? DispatchBusy.Mode.TACTICS
                        : spirit.runsBackgroundTask() ? DispatchBusy.Mode.BACKGROUND : DispatchBusy.Mode.WORK;
        String busyWith = DispatchBusy.text(
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
                stockTools,
                SpiritVitals.needs(spirit),
                List.copyOf(ToolWear.worn(spirit)));
    }
}
