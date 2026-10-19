package com.theminespirit.core.movement;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Личное пространство игрока (владелец 19.10): дух не стоит вплотную к игроку. Подошёл игрок ближе
 * {@code minDistance} — дух отходит на клетку в пределах {@code maxStep} по горизонтали, где до каждого игрока не
 * меньше {@code keepDistance}, и продолжает работу; проверка — раз в {@code checkIntervalTicks}. Числа — горячая
 * таблица {@code movement/personal_space.json}.
 */
public record PersonalSpace(double minDistance, double keepDistance, int maxStep, int checkIntervalTicks) {
    public static final String RESOURCE = "data/theminespirit/movement/personal_space.json";
    private static final Set<String> FIELDS = Set.of("minDistance", "keepDistance", "maxStep", "checkIntervalTicks");
    private static final double CENTER = 0.5;

    public record Position(double x, double y, double z) {}

    public PersonalSpace {
        if (minDistance <= 0 || keepDistance < minDistance || maxStep < 1 || checkIntervalTicks < 1) {
            throw new IllegalArgumentException("Личное пространство: minDistance = " + minDistance
                    + ", keepDistance = " + keepDistance + ", maxStep = " + maxStep + ", checkIntervalTicks = "
                    + checkIntervalTicks + ": расстояния положительные, отход не ближе порога, шаг и период ≥ 1");
        }
    }

    public boolean crowded(Position spirit, List<Position> players) {
        Objects.requireNonNull(spirit, "Позиция духа не может быть null");
        Objects.requireNonNull(players, "Список игроков не может быть null: пустой — значит, рядом никого");
        for (Position player : players) {
            if (distance(spirit, player) < minDistance) {
                return true;
            }
        }
        return false;
    }

    public Optional<BlockCoord> retreat(BlockCoord feet, List<Position> players, Predicate<BlockCoord> standable) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда отходить");
        Objects.requireNonNull(players, "Список игроков не может быть null");
        Objects.requireNonNull(standable, "Правило стоянки не может быть null: иначе дух отойдёт в стену");
        BlockCoord best = null;
        double bestClearance = Double.NEGATIVE_INFINITY;
        int bestStep = Integer.MAX_VALUE;
        for (int dx = -maxStep; dx <= maxStep; dx++) {
            for (int dz = -maxStep; dz <= maxStep; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    BlockCoord cell = new BlockCoord(feet.x() + dx, feet.y() + dy, feet.z() + dz);
                    double clearance = Math.min(keepDistance, clearance(cell, players));
                    if (clearance < minDistance) {
                        continue;
                    }
                    int step = Math.abs(dx) + Math.abs(dz) + Math.abs(dy);
                    boolean better = clearance > bestClearance
                            || (clearance == bestClearance && step < bestStep)
                            || (clearance == bestClearance
                                    && step == bestStep
                                    && best != null
                                    && cell.compareTo(best) < 0);
                    if (better && standable.test(cell)) {
                        best = cell;
                        bestClearance = clearance;
                        bestStep = step;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static double clearance(BlockCoord cell, List<Position> players) {
        Position center = new Position(cell.x() + CENTER, cell.y(), cell.z() + CENTER);
        double nearest = Double.MAX_VALUE;
        for (Position player : players) {
            nearest = Math.min(nearest, distance(center, player));
        }
        return nearest;
    }

    private static double distance(Position a, Position b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static PersonalSpace parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "personal_space.json");
        try {
            return new PersonalSpace(
                    JsonResource.decimal(root.get("minDistance"), origin, "minDistance"),
                    JsonResource.decimal(root.get("keepDistance"), origin, "keepDistance"),
                    JsonResource.integer(root.get("maxStep"), origin, "maxStep"),
                    JsonResource.integer(root.get("checkIntervalTicks"), origin, "checkIntervalTicks"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static PersonalSpace load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
