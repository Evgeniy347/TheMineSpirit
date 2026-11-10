package com.theminespirit.core.movement;

import java.util.Objects;
import java.util.Optional;

public record ProjectileTrajectory(double bodyMargin, int maxPredictionTicks) {
    private static final double EPSILON = 1e-9;

    public ProjectileTrajectory {
        if (bodyMargin < 0) {
            throw new IllegalArgumentException("Запас тела " + bodyMargin + " отрицателен: попадание не проверить");
        }
        if (maxPredictionTicks < 1) {
            throw new IllegalArgumentException(
                    "Горизонт предсказания " + maxPredictionTicks + " тиков: хотя бы один тик");
        }
    }

    public record Projectile(
            double x, double y, double z, double vx, double vy, double vz, double gravity, double drag) {
        public Projectile {
            if (gravity < 0 || drag < 0 || drag > 1) {
                throw new IllegalArgumentException("Снаряд: гравитация " + gravity + ", сопротивление " + drag
                        + ": гравитация не отрицательная, сопротивление в пределах [0, 1]");
            }
        }
    }

    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Границы тела: минимум (" + minX + ", " + minY + ", " + minZ
                        + ") больше максимума (" + maxX + ", " + maxY + ", " + maxZ + ")");
            }
        }

        public Bounds inflate(double margin) {
            return new Bounds(minX - margin, minY - margin, minZ - margin, maxX + margin, maxY + margin, maxZ + margin);
        }

        public Bounds shift(double dx, double dy, double dz) {
            return new Bounds(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
        }

        public double centerX() {
            return (minX + maxX) * 0.5;
        }

        public double centerY() {
            return (minY + maxY) * 0.5;
        }

        public double centerZ() {
            return (minZ + maxZ) * 0.5;
        }
    }

    public record Threat(int ticksToImpact, double x, double y, double z) {}

    public Optional<Threat> threat(Projectile projectile, Bounds body) {
        Objects.requireNonNull(projectile, "Снаряд не может быть null: без него нечего предсказывать");
        Objects.requireNonNull(body, "Тело духа не может быть null: без него не проверить попадание");
        Bounds box = body.inflate(bodyMargin);
        double towardX = box.centerX() - projectile.x();
        double towardY = box.centerY() - projectile.y();
        double towardZ = box.centerZ() - projectile.z();
        if (projectile.vx() * towardX + projectile.vy() * towardY + projectile.vz() * towardZ <= 0) {
            return Optional.empty();
        }
        double x = projectile.x();
        double y = projectile.y();
        double z = projectile.z();
        double vx = projectile.vx();
        double vy = projectile.vy();
        double vz = projectile.vz();
        for (int tick = 1; tick <= maxPredictionTicks; tick++) {
            double nextX = x + vx;
            double nextY = y + vy;
            double nextZ = z + vz;
            if (segmentHits(x, y, z, nextX, nextY, nextZ, box)) {
                return Optional.of(new Threat(tick, nextX, nextY, nextZ));
            }
            x = nextX;
            y = nextY;
            z = nextZ;
            vx *= projectile.drag();
            vy = vy * projectile.drag() - projectile.gravity();
            vz *= projectile.drag();
        }
        return Optional.empty();
    }

    private static boolean segmentHits(
            double x, double y, double z, double nextX, double nextY, double nextZ, Bounds box) {
        double low = 0;
        double high = 1;
        double[] start = {x, y, z};
        double[] delta = {nextX - x, nextY - y, nextZ - z};
        double[] min = {box.minX(), box.minY(), box.minZ()};
        double[] max = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < EPSILON) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) {
                    return false;
                }
                continue;
            }
            double first = (min[axis] - start[axis]) / delta[axis];
            double second = (max[axis] - start[axis]) / delta[axis];
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            low = Math.max(low, first);
            high = Math.min(high, second);
            if (low > high) {
                return false;
            }
        }
        return true;
    }
}
