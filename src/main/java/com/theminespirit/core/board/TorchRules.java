package com.theminespirit.core.board;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
        return next(stock, furnace, mine, TORCH_TARGET);
    }

    // target — сколько факелов держать: обычный запас или больше, когда их ждёт шаг (ограда с факелами на столбах).
    public static Optional<TorchStep> next(Map<String, Integer> stock, boolean furnace, boolean mine, int target) {
        return next(stock, furnace, mine, target, null);
    }

    // log — бревно породы поселения: при печи и без свободных брёвен добываются брёвна на уголь, а не уголь из
    // жилы — жила кончается, и провалы «добыто 0 из 2» запирали духам добычу (простой 6547 тиков, «джунгли»).
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
        // Порода известна, а печи ещё нет — ждать печь (она в цепочке до света): уголь из жилы кончается, и провал
        // «добыто 2 из 4» ставил духу паузу добычи — простой 2622 тика в начале этапа (приёмка «джунгли» 24.10).
        if (!mine || log != null) {
            return charcoalStep;
        }
        return Optional.of(new TorchStep(BoardCase.GATHER_MATERIALS, COAL, wanted));
    }

    public static boolean handles(String item) {
        return TORCH.equals(item) || COAL.equals(item) || CHARCOAL.equals(item);
    }
}
