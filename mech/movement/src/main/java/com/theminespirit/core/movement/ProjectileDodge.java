package com.theminespirit.core.movement;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.movement.ProjectileTrajectory.Bounds;
import com.theminespirit.core.movement.ProjectileTrajectory.Projectile;
import com.theminespirit.core.movement.ProjectileTrajectory.Threat;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.TableSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record ProjectileDodge(
        boolean enabled,
        double scanRadius,
        int minReactionTicks,
        int maxPredictionTicks,
        int cooldownTicks,
        double stepSpeed,
        double bodyMargin,
        double mobClearance) {
    public static final String RESOURCE = "data/theminespirit/combat/dodge.json";
    private static final Set<String> FIELDS = Set.of(
            "enabled",
            "scanRadius",
            "minReactionTicks",
            "maxPredictionTicks",
            "cooldownTicks",
            "stepSpeed",
            "bodyMargin",
            "mobClearance");
    private static final double EPSILON = 1e-9;
    private static final int[][] AXES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final TableSlot<ProjectileDodge> SOURCE = new TableSlot<>(ProjectileDodge::load);

    public record Position(double x, double y, double z) {}

    public record Dodge(int dx, int dz) {
        public Dodge {
            if (Math.abs(dx) + Math.abs(dz) != 1) {
                throw new IllegalArgumentException("Шаг уклонения (" + dx + ", " + dz
                        + "): ровно одна ось на единицу, иначе дух не сделает честный шаг в соседнюю клетку");
            }
        }
    }

    public record Request(
            List<Projectile> projectiles,
            Bounds body,
            BlockCoord feet,
            List<Position> mobs,
            int lastDodgeTick,
            int nowTick) {
        public Request {
            projectiles = List.copyOf(
                    Objects.requireNonNull(projectiles, "Снаряды не могут быть null: пустой список — нет угрозы"));
            Objects.requireNonNull(body, "Тело духа не может быть null: без него не проверить попадание");
            Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них не выбрать клетку шага");
            mobs = List.copyOf(Objects.requireNonNull(mobs, "Мобы не могут быть null: пустой список — рядом никого"));
        }
    }

    public ProjectileDodge {
        if (scanRadius <= 0) {
            throw new IllegalArgumentException("Уклонение: радиус поиска " + scanRadius + " не положителен");
        }
        if (minReactionTicks < 1 || maxPredictionTicks < minReactionTicks) {
            throw new IllegalArgumentException("Уклонение: реакция " + minReactionTicks + " тиков, предсказание "
                    + maxPredictionTicks + ": реакция хотя бы 1 тик и не больше горизонта");
        }
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("Уклонение: кулдаун " + cooldownTicks + " отрицателен");
        }
        if (stepSpeed <= 0 || bodyMargin < 0 || mobClearance < 0) {
            throw new IllegalArgumentException("Уклонение: скорость шага " + stepSpeed + ", запас тела " + bodyMargin
                    + ", зазор от мобов " + mobClearance
                    + ": скорость шага положительна, запасы не отрицательны");
        }
    }

    public Optional<Threat> threat(Projectile projectile, Bounds body) {
        return trajectory().threat(projectile, body);
    }

    public Optional<Dodge> decide(Request request, Predicate<BlockCoord> standable) {
        Objects.requireNonNull(request, "Запрос на уклонение не может быть null: решать не по чему");
        Objects.requireNonNull(standable, "Правило стоянки не может быть null: иначе шаг уведёт в обрыв");
        if (!enabled || request.projectiles().isEmpty()) {
            return Optional.empty();
        }
        if (request.lastDodgeTick() >= 0 && request.nowTick() - request.lastDodgeTick() < cooldownTicks) {
            return Optional.empty();
        }
        ProjectileTrajectory trajectory = trajectory();
        Threat nearest = null;
        Projectile source = null;
        for (Projectile projectile : request.projectiles()) {
            Optional<Threat> threat = trajectory.threat(projectile, request.body());
            if (threat.isPresent() && (nearest == null || threat.get().ticksToImpact() < nearest.ticksToImpact())) {
                nearest = threat.get();
                source = projectile;
            }
        }
        if (nearest == null || source == null || nearest.ticksToImpact() < minReactionTicks) {
            return Optional.empty();
        }
        return choose(request, standable, source, trajectory);
    }

    private Optional<Dodge> choose(
            Request request, Predicate<BlockCoord> standable, Projectile source, ProjectileTrajectory trajectory) {
        double alongX = source.vx();
        double alongZ = source.vz();
        double along = Math.hypot(alongX, alongZ);
        if (along < EPSILON) {
            alongX = request.body().centerX() - source.x();
            alongZ = request.body().centerZ() - source.z();
            along = Math.hypot(alongX, alongZ);
        }
        if (along < EPSILON) {
            alongX = 1;
            alongZ = 0;
            along = 1;
        }
        Dodge best = null;
        double bestAlong = Double.MAX_VALUE;
        for (int[] axis : AXES) {
            BlockCoord cell = new BlockCoord(
                    request.feet().x() + axis[0],
                    request.feet().y(),
                    request.feet().z() + axis[1]);
            if (!standable.test(cell) || crowded(request.mobs(), cell)) {
                continue;
            }
            Bounds shifted = request.body().shift(axis[0], 0, axis[1]);
            if (request.projectiles().stream()
                    .anyMatch(
                            projectile -> trajectory.threat(projectile, shifted).isPresent())) {
                continue;
            }
            double alongShare = Math.abs(alongX * axis[0] + alongZ * axis[1]) / along;
            boolean better = alongShare < bestAlong
                    || (Double.compare(alongShare, bestAlong) == 0
                            && (best == null || axis[0] < best.dx() || (axis[0] == best.dx() && axis[1] < best.dz())));
            if (better) {
                best = new Dodge(axis[0], axis[1]);
                bestAlong = alongShare;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean crowded(List<Position> mobs, BlockCoord cell) {
        double x = cell.x() + 0.5;
        double y = cell.y();
        double z = cell.z() + 0.5;
        for (Position mob : mobs) {
            double dx = mob.x() - x;
            double dy = mob.y() - y;
            double dz = mob.z() - z;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < mobClearance) {
                return true;
            }
        }
        return false;
    }

    private ProjectileTrajectory trajectory() {
        return new ProjectileTrajectory(bodyMargin, maxPredictionTicks);
    }

    public static ProjectileDodge parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "dodge.json");
        try {
            return new ProjectileDodge(
                    booleanField(root, origin, "enabled"),
                    JsonResource.decimal(root.get("scanRadius"), origin, "scanRadius"),
                    JsonResource.integer(root.get("minReactionTicks"), origin, "minReactionTicks"),
                    JsonResource.integer(root.get("maxPredictionTicks"), origin, "maxPredictionTicks"),
                    JsonResource.integer(root.get("cooldownTicks"), origin, "cooldownTicks"),
                    JsonResource.decimal(root.get("stepSpeed"), origin, "stepSpeed"),
                    JsonResource.decimal(root.get("bodyMargin"), origin, "bodyMargin"),
                    JsonResource.decimal(root.get("mobClearance"), origin, "mobClearance"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static ProjectileDodge load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static ProjectileDodge current() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<ProjectileDodge> supplier) {
        SOURCE.use(supplier);
    }

    private static boolean booleanField(JsonNode root, String origin, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': у " + field + " ожидается true или false: иначе навык не включить");
        }
        return node.asBoolean();
    }
}
