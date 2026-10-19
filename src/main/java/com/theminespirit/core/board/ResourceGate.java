package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Гейт по ресурсам: задание берётся в работу только при наличии требуемых предметов.
 * Не хватает — задание висит в очереди, а наблюдателю видно, чего и сколько не хватает.
 */
public final class ResourceGate {
    private ResourceGate() {}

    public static GateOutcome gate(StockNeed need, StockView stock) {
        return missing(need, stock).isEmpty() ? GateOutcome.READY : GateOutcome.WAITING;
    }

    public static Map<String, Integer> missing(StockNeed need, StockView stock) {
        Objects.requireNonNull(need, "need не может быть null: гейт без нужды не проверить");
        Objects.requireNonNull(stock, "stock не может быть null: гейт без снимка запасов не проверить");
        Map<String, Integer> missing = new HashMap<>();
        for (Map.Entry<String, Integer> entry : need.items().entrySet()) {
            int shortage = entry.getValue() - stock.available().getOrDefault(entry.getKey(), 0);
            if (shortage > 0) {
                missing.put(entry.getKey(), shortage);
            }
        }
        return Map.copyOf(missing);
    }
}
