package com.theminespirit.core.board;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class TorchRules {
    public static final String TORCH = "torch";
    public static final int TORCH_TARGET = 16;
    public static final int TORCHES_PER_CRAFT = 4;
    public static final int CHARCOAL_BATCH = 2;
    public static final int CHARCOAL_LOGS = 4;
    public static final String COAL = "coal";
    private static final String CHARCOAL = "charcoal";
    private static final String LOG_SUFFIX = "_log";

    private TorchRules() {}

    public record TorchStep(BoardCase boardCase, String item, int count) {
        public TorchStep {
            Objects.requireNonNull(boardCase, "Кейс шага факелов не может быть null");
            Objects.requireNonNull(item, "Предмет шага факелов не может быть null");
            if (count < 1) {
                throw new IllegalArgumentException("Шаг факелов на " + count + " предметов: нечего делать");
            }
        }
    }

    public static Optional<TorchStep> next(Map<String, Integer> stock, boolean furnace) {
        return next(stock, furnace, false);
    }

    public static Optional<TorchStep> next(Map<String, Integer> stock, boolean furnace, boolean mine) {
        return next(stock, furnace, mine, TORCH_TARGET);
    }

    public static Optional<TorchStep> next(Map<String, Integer> stock, boolean furnace, boolean mine, int target) {
        return next(stock, furnace, mine, target, null);
    }

    public static Optional<TorchStep> next(
            Map<String, Integer> stock, boolean furnace, boolean mine, int target, @Nullable String log) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        int torches = stock.getOrDefault(TORCH, 0);
        if (torches >= target) {
            return Optional.empty();
        }
        int coal = stock.getOrDefault(COAL, 0);
        int charcoal = stock.getOrDefault(CHARCOAL, 0);
        int wanted = (target - torches + TORCHES_PER_CRAFT - 1) / TORCHES_PER_CRAFT;
        if (coal + charcoal > 0) {
            String fuel = coal >= charcoal ? COAL : CHARCOAL;
            int crafts = Math.min(wanted, Math.max(coal, charcoal));
            return Optional.of(new TorchStep(BoardCase.CRAFT_TORCHES, fuel, crafts * TORCHES_PER_CRAFT));
        }
        Optional<TorchStep> charcoalStep = furnace
                ? stock.entrySet().stream()
                        .filter(entry -> entry.getKey().endsWith(LOG_SUFFIX) && entry.getValue() >= CHARCOAL_LOGS)
                        .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                        .map(entry -> new TorchStep(BoardCase.SMELT_CHARCOAL, entry.getKey(), CHARCOAL_BATCH))
                : Optional.empty();
        if (charcoalStep.isPresent()) {
            return charcoalStep;
        }
        if (furnace && mine && log != null) {
            return Optional.of(new TorchStep(BoardCase.GATHER_MATERIALS, log, CHARCOAL_LOGS));
        }
        if (!mine || log != null) {
            return charcoalStep;
        }
        return Optional.of(new TorchStep(BoardCase.GATHER_MATERIALS, COAL, wanted));
    }

    public static boolean handles(String item) {
        return TORCH.equals(item) || COAL.equals(item) || CHARCOAL.equals(item);
    }
}
