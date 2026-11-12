package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.SpiritSupplies;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

final class IssueSteps {
    static final long SLOW_MILLIS = 200;
    static final long LOG_TICKS = 1200;

    private final Map<String, Long> current = new LinkedHashMap<>();
    private Map<String, Long> worst = Map.of();
    private long worstTotal;
    private long started;
    private long loggedAt = -LOG_TICKS;

    void begin() {
        current.clear();
        started = System.nanoTime();
    }

    void add(String step, long startedNanos) {
        current.merge(step, System.nanoTime() - startedNanos, Long::sum);
    }

    long lap(String step, long startedNanos) {
        add(step, startedNanos);
        return System.nanoTime();
    }

    void end(long now, Logger logger) {
        long total = (System.nanoTime() - started) / 1_000_000;
        if (total > worstTotal) {
            worstTotal = total;
            Map<String, Long> millis = new LinkedHashMap<>();
            current.forEach((step, nanos) -> millis.put(step, nanos / 1_000_000));
            worst = millis;
        }
        if (worstTotal >= SLOW_MILLIS && now - loggedAt >= LOG_TICKS) {
            loggedAt = now;
            logger.warn("[board] худшая раздача за {} тиков: {} мс: {}", LOG_TICKS, worstTotal, worst);
            worstTotal = 0;
            worst = Map.of();
        }
    }

    long worstTotal() {
        return worstTotal;
    }

    static TicketTarget gatherOnTop(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        String item = target.supplyItem();
        if (ticket.boardCase() != BoardCase.GATHER_MATERIALS
                || item == null
                || "coal".equals(item)
                || "cobblestone".equals(item)) {
            return target;
        }
        int held = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT).inInventory(item);
        return held <= 0
                ? target
                : target.withSupply(item, Math.min(CatalogLimits.COUNT_MAX, held + target.supplyCount()));
    }

    static Optional<TicketTarget> fitTransfer(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                || target.transferFrom() == null
                || !target.transferFrom().equals(spirit.getUUID())
                || target.supplyItem() == null) {
            return Optional.of(target);
        }
        int held = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT).inInventory(target.supplyItem());
        if (held <= 0) {
            return Optional.empty();
        }
        return Optional.of(
                held >= target.supplyCount()
                        ? target
                        : target.withTransfer(target.transferFrom(), target.transferTo(), target.supplyItem(), held));
    }
}
