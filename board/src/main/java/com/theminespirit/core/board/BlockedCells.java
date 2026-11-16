package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlockedCells {
    public record Mark(BlockCoord cell, long since) {}

    private record Held(long since, long until) {}

    private final long ttl;
    private final Map<BlockCoord, Held> cells = new HashMap<>();

    public BlockedCells(long ttl) {
        if (ttl < 1) {
            throw new IllegalArgumentException(
                    "Срок жизни метки " + ttl + " не положителен: метка не переживёт тик, преграду никто не снесёт");
        }
        this.ttl = ttl;
    }

    public void note(BlockCoord cell, long now) {
        Objects.requireNonNull(cell, "Клетка преграды не может быть null: без неё метку не найти");
        Held held = cells.get(cell);
        long since = held == null || held.until() < now ? now : held.since();
        cells.put(cell, new Held(since, now + ttl));
    }

    public List<Mark> live(long now) {
        cells.entrySet().removeIf(entry -> entry.getValue().until() < now);
        List<Mark> marks = new ArrayList<>(cells.size());
        for (Map.Entry<BlockCoord, Held> entry : cells.entrySet()) {
            marks.add(new Mark(entry.getKey(), entry.getValue().since()));
        }
        return List.copyOf(marks);
    }
}
