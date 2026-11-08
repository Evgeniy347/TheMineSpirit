package com.theminespirit.core.ui;

import com.theminespirit.core.board.BoardCase;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record SettlementTreeFacts(Map<String, Fact> byKey) {
    public record Fact(
            int percent,
            boolean ready,
            boolean unknown,
            Map<String, Integer> missing,
            SettlementTree.@Nullable Position at) {
        public Fact {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException("Готовность постройки " + percent + "%: процент лежит от 0 до 100");
            }
            if (unknown && ready) {
                throw new IllegalArgumentException(
                        "Постройка одновременно готова и не проверена: незагруженный чанк — это не готово");
            }
            missing = Map.copyOf(Objects.requireNonNull(
                    missing, "Недостача постройки не может быть null: пустую задают пустой картой"));
        }
    }

    public SettlementTreeFacts {
        byKey = Map.copyOf(Objects.requireNonNull(byKey, "Состояние мира не может быть null: без него — пустая карта"));
    }

    public static SettlementTreeFacts empty() {
        return new SettlementTreeFacts(Map.of());
    }

    public static String boardKey(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс постройки не может быть null: по нему берётся состояние мира");
        return "case:" + boardCase.name() + ":" + level(level, boardCase.name());
    }

    public static String planKey(String planId, int level) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему берётся состояние мира");
        if (planId.isBlank()) {
            throw new IllegalArgumentException("id постройки плана пуст: состояние мира ищут по непустому имени");
        }
        return "plan:" + planId + ":" + level(level, planId);
    }

    public @Nullable Fact fact(String key) {
        Objects.requireNonNull(key, "Ключ состояния не может быть null: без него не найти постройку");
        return byKey.get(key);
    }

    private static int level(int level, String owner) {
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень " + level + " у постройки '" + owner + "': состояние мира берут с первого уровня");
        }
        return level;
    }
}
