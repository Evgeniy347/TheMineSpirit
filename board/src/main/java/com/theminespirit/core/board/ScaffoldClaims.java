package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ScaffoldClaims {
    public record Claim(String id, UUID executor, long expiresAt, Set<BlockCoord> cells) {
        public Claim {
            Objects.requireNonNull(id, "id подмости не может быть null: по нему снимается защита");
            Objects.requireNonNull(executor, "Исполнитель подмости не может быть null: иначе ломать некому");
            cells = Set.copyOf(Objects.requireNonNull(cells, "Клетки подмости не могут быть null"));
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("Подмость без клеток: защищать нечего");
            }
        }
    }

    private final Map<String, Claim> claims = new LinkedHashMap<>();

    public void claim(Claim claim) {
        Objects.requireNonNull(claim, "Подмость не может быть null");
        claims.put(claim.id(), claim);
    }

    public void expire(long now) {
        claims.values().removeIf(claim -> claim.expiresAt() < now);
    }

    public void removed(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null");
        List<String> gone = new ArrayList<>();
        claims.forEach((id, claim) -> {
            if (!claim.cells().contains(cell)) {
                return;
            }
            Set<BlockCoord> left = new LinkedHashSet<>(claim.cells());
            left.remove(cell);
            if (left.isEmpty()) {
                gone.add(id);
            } else {
                claims.put(id, new Claim(claim.id(), claim.executor(), claim.expiresAt(), left));
            }
        });
        gone.forEach(claims::remove);
    }

    public void remove(String id) {
        claims.remove(id);
    }

    public List<Claim> claims(long now) {
        expire(now);
        return List.copyOf(claims.values());
    }

    public boolean claimed(BlockCoord cell, long now) {
        expire(now);
        return claims.values().stream().anyMatch(claim -> claim.cells().contains(cell));
    }

    public boolean blocked(UUID spirit, BlockCoord cell, long now) {
        expire(now);
        return claims.values().stream()
                .anyMatch(claim ->
                        claim.cells().contains(cell) && !claim.executor().equals(spirit));
    }

    public Set<BlockCoord> protectedCells(long now) {
        expire(now);
        Set<BlockCoord> cells = new LinkedHashSet<>();
        claims.values().forEach(claim -> cells.addAll(claim.cells()));
        return Set.copyOf(cells);
    }
}
