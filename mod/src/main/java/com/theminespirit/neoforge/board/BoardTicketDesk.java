package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BuildProgressSnapshots;
import com.theminespirit.core.board.TicketRun;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class BoardTicketDesk {
    private static final Logger LOGGER = LogUtils.getLogger();

    final BuildProgressSnapshots progress = new BuildProgressSnapshots();
    private final BoardService host;
    private final Board board;
    private final Map<String, TicketTarget> targets;
    private final Map<UUID, String> orphans = new HashMap<>();

    BoardTicketDesk(BoardService host, Board board, Map<String, TicketTarget> targets) {
        this.host = host;
        this.board = board;
        this.targets = targets;
    }

    boolean reopen(String ticketId) {
        boolean reopened = board.reopen(ticketId);
        if (reopened) {
            targets.remove(ticketId);
            host.persistBoard();
        }
        return reopened;
    }

    boolean cancel(ServerLevel level, String ticketId) {
        Map<UUID, Set<String>> holders = new HashMap<>();
        collectHolders(ticketId, holders, new HashSet<>());
        boolean cancelled = board.cancel(ticketId);
        if (!cancelled) {
            return false;
        }
        targets.keySet().removeIf(id -> board.statusOf(id).orElse(null) == BoardStatus.CANCELLED);
        for (Map.Entry<UUID, Set<String>> holder : holders.entrySet()) {
            if (!(level.getEntity(holder.getKey()) instanceof SpiritEntity spirit)) {
                continue;
            }
            if (!ownsAny(holder.getValue(), spirit)) {
                LOGGER.info(
                        "[board] задание {} снято с доски, но духа {} не трогаем: он выполняет другую задачу",
                        ticketId,
                        spirit.displayName());
                continue;
            }
            spirit.cancelTask();
            WorkReserveHub.releaseAll(spirit);
        }
        host.persistBoard();
        return true;
    }

    private boolean ownsAny(Set<String> ticketIds, SpiritEntity spirit) {
        for (String id : ticketIds) {
            if (TicketRun.ownsTask(host.issuedGeneration(id), spirit.taskGeneration())) {
                return true;
            }
        }
        return false;
    }

    void cancelOrphans(ServerLevel level, List<SpiritEntity> spirits) {
        if (orphans.isEmpty()) {
            return;
        }
        for (SpiritEntity spirit : spirits) {
            String ticket = orphans.remove(spirit.getUUID());
            boolean holdsTicket = !board.assignedTo(spirit.getUUID()).isEmpty();
            if (ticket == null || spirit.isRemoved() || holdsTicket) {
                continue;
            }
            if (!TicketRun.restoredTask(spirit.taskGeneration())) {
                LOGGER.info(
                        "[board] сброс сборки не трогает духа {}: он уже выполняет другую задачу, а старое "
                                + "задание {} остаётся снятым с доски",
                        spirit.displayName(),
                        ticket);
                continue;
            }
            if (spirit.taskStatus().orElse(null) == RunStatus.RUNNING) {
                spirit.cancelTask();
                LOGGER.warn(
                        "[incident] дух {}: задание {} снято сбросом при смене сборки — его задача {} отменена, дух "
                                + "свободен для доски; {}",
                        spirit.displayName(),
                        ticket,
                        spirit.currentTaskScript().orElse("-"),
                        IncidentSnapshots.record(level, spirit, "сброс сборки снял задание " + ticket));
            }
        }
    }

    List<String> resetForNewRules() {
        Map<String, UUID> holders = new HashMap<>();
        for (BoardTicket ticket : board.tickets()) {
            board.holderOf(ticket.id()).ifPresent(holder -> holders.put(ticket.id(), holder));
        }
        List<String> reset = board.resetForNewRules();
        for (String id : reset) {
            UUID holder = holders.get(id);
            if (holder != null) {
                orphans.put(holder, id);
            }
        }
        return reset;
    }

    Optional<TicketTarget> targetOf(String ticketId) {
        if (ticketId == null) {
            throw new IllegalArgumentException("ticketId не может быть null: цель ищется у конкретного задания");
        }
        return Optional.ofNullable(targets.get(ticketId));
    }

    Optional<TicketTarget> targetOf(String ticketId, ServerLevel level, List<SpiritEntity> spirits) {
        TicketTarget stored = targets.get(ticketId);
        if (stored != null) {
            return Optional.of(stored);
        }
        return HomePoint.of(level, spirits).map(TicketTarget::at);
    }

    boolean hasTarget(String ticketId) {
        return targets.containsKey(ticketId);
    }

    private void collectHolders(String ticketId, Map<UUID, Set<String>> holders, Set<String> visited) {
        if (!visited.add(ticketId)) {
            return;
        }
        board.holderOf(ticketId)
                .ifPresent(holder ->
                        holders.computeIfAbsent(holder, id -> new HashSet<>()).add(ticketId));
        board.find(ticketId)
                .ifPresent(ticket -> ticket.executors()
                        .forEach(executor -> holders.computeIfAbsent(executor, id -> new HashSet<>())
                                .add(ticketId)));
        for (BoardTicket ticket : board.tickets()) {
            if (ticket.dependsOn().contains(ticketId)) {
                collectHolders(ticket.id(), holders, visited);
            }
        }
    }
}
