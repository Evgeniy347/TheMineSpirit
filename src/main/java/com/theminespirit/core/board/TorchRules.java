package com.theminespirit.core.board;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Путь «уголь → факел»: есть уголь или древесный уголь — сразу задание «сделать факелы» до запаса; угля нет, а печь
 * стоит (или лежит у духа) — «пережечь пару брёвен в древесный уголь», топливо — брёвна/доски; пережечь нечего, а
 * шахта уже есть — «добыть уголь» в шахте (порядок владельца 21.10: уголь в шахте → факелы; INC-25: без этого шага
 * свет первых шагов ждал факелы вечно). Решение — по снимку запасов; числа — константы правила.
 */
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
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        int torches = stock.getOrDefault(TORCH, 0);
        if (torches >= TORCH_TARGET) {
            return Optional.empty();
        }
        int coal = stock.getOrDefault(COAL, 0);
        int charcoal = stock.getOrDefault(CHARCOAL, 0);
        int wanted = (TORCH_TARGET - torches + TORCHES_PER_CRAFT - 1) / TORCHES_PER_CRAFT;
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
        if (charcoalStep.isPresent() || !mine) {
            return charcoalStep;
        }
        return Optional.of(new TorchStep(BoardCase.GATHER_MATERIALS, COAL, wanted));
    }

    public static boolean handles(String item) {
        return TORCH.equals(item) || COAL.equals(item) || CHARCOAL.equals(item);
    }
}
