package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class SupplyGate {

    public record Request(String item, int count) {
        public Request {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Заявка без предмета: снабжение не знает, что нести");
            }
            if (count <= 0) {
                throw new IllegalArgumentException(
                        "Заявка на '" + item + "' с количеством " + count + ": нехватка всегда положительна");
            }
        }
    }

    public record Decision(List<Request> supply, List<Request> gather) {
        public Decision {
            Objects.requireNonNull(supply, "Снабжение не может быть null: пустой список задают пустым");
            Objects.requireNonNull(gather, "Добыча не может быть null: пустой список задают пустым");
            supply = List.copyOf(supply);
            gather = List.copyOf(gather);
        }

        public boolean isEmpty() {
            return supply.isEmpty() && gather.isEmpty();
        }
    }

    private SupplyGate() {}

    public static Decision decide(
            Map<String, Integer> missing,
            Map<String, Integer> stock,
            int keep,
            Map<String, Long> closedAt,
            long now,
            long backoff) {
        Objects.requireNonNull(missing, "Нехватка не может быть null: пустую задают пустой картой");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(closedAt, "Откаты закрытого снабжения не могут быть null: пустые задают пустой картой");
        if (keep < 0) {
            throw new IllegalArgumentException(
                    "Неприкосновенный минимум " + keep + " отрицателен: минимум не бывает меньше нуля");
        }
        if (backoff < 0) {
            throw new IllegalArgumentException("Откат " + backoff + " отрицателен: пауза не бывает меньше нуля");
        }
        List<Request> supply = new ArrayList<>();
        List<Request> gather = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(missing).entrySet()) {
            String item = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) {
                continue;
            }
            int takeable = Math.max(0, stock.getOrDefault(item, 0) - keep);
            if (takeable <= 0) {
                gather.add(new Request(item, count));
                continue;
            }
            String id = "supply:" + item;
            Long closed = closedAt.get(id);
            if (closed != null && now - closed < backoff) {
                continue;
            }
            supply.add(new Request(item, count));
        }
        return new Decision(supply, gather);
    }
}
