package com.theminespirit.core.movement;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Выход из застревания телепортом (упрощение 44.6, согласовано владельцем 18.10): дух, который не сдвинулся к цели,
 * переносится на клетку в пределах {@code maxDistance} ближе к цели, где можно стоять и куда виден путь — не сквозь
 * стены. Не больше {@code teleportsPerApproach} переносов на один подход, дальше — обычный отказ «застрял».
 * Числа — горячая таблица {@code movement/unstuck.json}.
 */
public record Unstuck(int maxDistance, int teleportsPerApproach) {
    public static final String RESOURCE = "data/theminespirit/movement/unstuck.json";
    private static final Set<String> FIELDS = Set.of("maxDistance", "teleportsPerApproach");
    private static final int DOWN = 2;
    private static final int UP = 2;

    public Unstuck {
        if (maxDistance < 1 || teleportsPerApproach < 0) {
            throw new IllegalArgumentException("Телепорт при застревании: maxDistance = " + maxDistance
                    + ", teleportsPerApproach = " + teleportsPerApproach
                    + ": расстояние хотя бы 1, число переносов не отрицательное");
        }
    }

    public Optional<BlockCoord> step(
            BlockCoord feet, BlockCoord target, Predicate<BlockCoord> standable, Predicate<BlockCoord> clearPath) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда переносить");
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, куда ближе");
        Objects.requireNonNull(standable, "Правило стоянки не может быть null");
        Objects.requireNonNull(clearPath, "Правило видимого пути не может быть null: иначе перенос сквозь стену");
        double from = distanceSquared(feet, target);
        BlockCoord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -maxDistance; dx <= maxDistance; dx++) {
            for (int dz = -maxDistance; dz <= maxDistance; dz++) {
                for (int dy = -DOWN; dy <= UP; dy++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockCoord cell = new BlockCoord(feet.x() + dx, feet.y() + dy, feet.z() + dz);
                    double distance = distanceSquared(cell, target);
                    boolean better = best == null
                            ? distance < from
                            : distance < bestDistance || (distance == bestDistance && cell.compareTo(best) < 0);
                    if (better && standable.test(cell) && clearPath.test(cell)) {
                        best = cell;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Unstuck parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "unstuck.json");
        try {
            return new Unstuck(
                    JsonResource.integer(root.get("maxDistance"), origin, "maxDistance"),
                    JsonResource.integer(root.get("teleportsPerApproach"), origin, "teleportsPerApproach"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static Unstuck load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    private static double distanceSquared(BlockCoord a, BlockCoord b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
