package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.StuckCause;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record GraphFeedback(Clock clock, Player player, List<Failure> failures) {
    public GraphFeedback {
        Objects.requireNonNull(clock, "Время суток не может быть null: неизвестное задают Clock.unknown()");
        Objects.requireNonNull(player, "Факт игрока не может быть null: без игрока — Player.none()");
        failures = List.copyOf(
                Objects.requireNonNull(failures, "Память неудач не может быть null: без провалов — пустой список"));
    }

    public static GraphFeedback none() {
        return new GraphFeedback(Clock.unknown(), Player.none(), List.of());
    }

    public record Clock(boolean morning, boolean duskSoon, boolean night, long dayTime) {
        public Clock {
            if (dayTime < -1) {
                throw new IllegalArgumentException(
                        "Время суток " + dayTime + " отрицательно: неизвестное время задают -1, а не минусом");
            }
        }

        public static Clock of(long dayTime) {
            long time = Math.floorMod(dayTime, 24000L);
            return new Clock(
                    time >= 23000L || time < 6000L,
                    (time >= 11500L && time < 13000L) || time >= 22500L,
                    time >= 13000L && time < 23000L,
                    time);
        }

        public static Clock unknown() {
            return new Clock(false, false, false, -1);
        }
    }

    public record Player(
            GraphFacts.@Nullable Position position,
            boolean near,
            boolean holdingBlock,
            boolean usingItem,
            boolean digging,
            float health,
            float maxHealth,
            int food) {
        public Player(
                GraphFacts.@Nullable Position position,
                boolean near,
                boolean holdingBlock,
                boolean usingItem,
                boolean digging) {
            this(position, near, holdingBlock, usingItem, digging, 0, 0, 0);
        }

        public Player {
            if (health < 0 || maxHealth < 0 || health > maxHealth) {
                throw new IllegalArgumentException("Здоровье игрока " + health + " из " + maxHealth
                        + ": оно не отрицательно и не больше максимума");
            }
            if (food < 0) {
                throw new IllegalArgumentException("Еда игрока " + food + " отрицательна: голод начинается с нуля");
            }
        }

        public static Player none() {
            return new Player(null, false, false, false, false);
        }
    }

    public enum Cause {
        NO_PATH,
        NOT_REACHED,
        NO_MATERIAL,
        CHUNK,
        OTHER;

        public static Cause of(@Nullable String reason) {
            if (reason == null || reason.isBlank()) {
                return OTHER;
            }
            String text = reason.toLowerCase(Locale.ROOT);
            if (text.contains("чанк") || text.contains("chunk") || text.contains("не загружен")) {
                return CHUNK;
            }
            if (StuckCause.noPath(reason)) {
                return NO_PATH;
            }
            if (text.contains("не дошёл")
                    || text.contains("не дошел")
                    || text.contains("застрял")
                    || text.contains("стоянк")
                    || text.contains("преград")) {
                return NOT_REACHED;
            }
            if (text.contains("нет материал")
                    || text.contains("не хватает")
                    || text.contains("нет предмет")
                    || text.contains("материал")
                    || text.contains("инструмент")) {
                return NO_MATERIAL;
            }
            return OTHER;
        }
    }

    public record Failure(String node, String target, int count, Cause cause, long lastTick, double weight) {
        public Failure {
            Objects.requireNonNull(node, "Узел провала не может быть null: по нему граф помнит неудачу");
            Objects.requireNonNull(target, "Цель провала не может быть null: неизвестную задают пустой строкой");
            Objects.requireNonNull(cause, "Причина провала не может быть null: неопознанную задают OTHER");
            if (count < 1) {
                throw new IllegalArgumentException(
                        "Провалов " + count + ": запись памяти заводится только после хотя бы одного провала");
            }
            if (lastTick < 0) {
                throw new IllegalArgumentException("Тик последнего провала " + lastTick
                        + " отрицателен: время идёт вперёд, неизвестное не записывают");
            }
            if (!Double.isFinite(weight) || weight < 0) {
                throw new IllegalArgumentException("Вес провала " + weight
                        + ": он неотрицателен и конечен, затухание считает память, а не тот, кто читает факт");
            }
        }

        public Failure(String node, String target, int count, Cause cause, long lastTick) {
            this(node, target, count, cause, lastTick, count);
        }
    }
}
