package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FillIndex {
    private static final FillIndex EMPTY = new FillIndex(Map.of(), Map.of());

    private final Map<BlockCoord, String> tickets;
    private final Map<String, Integer> order;

    private FillIndex(Map<BlockCoord, String> tickets, Map<String, Integer> order) {
        this.tickets = tickets;
        this.order = order;
    }

    public static FillIndex of(Map<String, PlotLeveling> open) {
        Objects.requireNonNull(open, "Открытые задания засыпки не могут быть null: пустые задают пустой картой");
        Map<BlockCoord, String> tickets = new HashMap<>();
        Map<String, Integer> order = new HashMap<>();
        for (Map.Entry<String, PlotLeveling> entry : open.entrySet()) {
            String id = Objects.requireNonNull(
                    entry.getKey(), "Номер задания засыпки не может быть null: по нему находят продолжение");
            PlotLeveling leveling = entry.getValue();
            if (leveling == null) {
                continue;
            }
            order.put(id, order.size());
            for (PlotLeveling.Box box : leveling.fill()) {
                for (int x = box.x1(); x <= box.x2(); x++) {
                    for (int y = box.y1(); y <= box.y2(); y++) {
                        for (int z = box.z1(); z <= box.z2(); z++) {
                            tickets.putIfAbsent(new BlockCoord(x, y, z), id);
                        }
                    }
                }
            }
        }
        if (tickets.isEmpty()) {
            return EMPTY;
        }
        return new FillIndex(Map.copyOf(tickets), Map.copyOf(order));
    }

    public Optional<String> covering(Collection<BlockCoord> pitCells) {
        Objects.requireNonNull(pitCells, "Клетки ямы не могут быть null: пустую яму задают пустым списком");
        String best = null;
        int bestOrder = Integer.MAX_VALUE;
        for (BlockCoord cell : pitCells) {
            String id = tickets.get(cell);
            if (id == null) {
                continue;
            }
            int rank = order.getOrDefault(id, Integer.MAX_VALUE);
            if (rank < bestOrder) {
                best = id;
                bestOrder = rank;
            }
        }
        return Optional.ofNullable(best);
    }
}
