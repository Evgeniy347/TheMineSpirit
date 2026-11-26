package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DispatchReach {
    public record Verdict(boolean reachable, String why) {
        public Verdict {
            if (why == null || why.isBlank()) {
                throw new IllegalArgumentException("Вердикт пути без причины: по нему не объяснить отказ выдачи");
            }
        }
    }

    public interface Cache {
        Optional<Verdict> verdict(UUID spirit, BlockCoord from, BlockCoord target);

        void request(UUID spirit, BlockCoord from, BlockCoord target);
    }

    private DispatchReach() {}

    public static boolean allows(Cache cache, UUID spirit, BlockCoord from, BlockCoord target) {
        Objects.requireNonNull(cache, "Кэш путей не может быть null: без него выдача не проверит достижимость");
        Objects.requireNonNull(spirit, "Дух не может быть null: путь проверяется для конкретного исполнителя");
        Objects.requireNonNull(from, "Начало пути не может быть null: без него неоткуда идти");
        Objects.requireNonNull(target, "Цель пути не может быть null: без неё некуда идти");
        Optional<Verdict> verdict = cache.verdict(spirit, from, target);
        if (verdict.isEmpty()) {
            cache.request(spirit, from, target);
            return false;
        }
        return verdict.orElseThrow().reachable();
    }
}
