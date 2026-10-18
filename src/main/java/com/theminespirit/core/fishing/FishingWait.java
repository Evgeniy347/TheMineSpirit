package com.theminespirit.core.fishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

/**
 * Время рыбалки без поплавка (упрощение 44.8): дух ждёт поклёвку случайно от {@code waitMinTicks} до
 * {@code waitMaxTicks} (минус скорость приманки удочки) и ещё {@code hookedMinTicks}…{@code hookedMaxTicks}, затем
 * получает улов по ванильной таблице лута. Числа — горячая таблица {@code fishing/wait.json}.
 */
public record FishingWait(int waitMinTicks, int waitMaxTicks, int hookedMinTicks, int hookedMaxTicks) {
    public static final String RESOURCE = "data/theminespirit/fishing/wait.json";
    private static final Set<String> FIELDS = Set.of("waitMinTicks", "waitMaxTicks", "hookedMinTicks",
            "hookedMaxTicks");

    public FishingWait {
        if (waitMinTicks < 0 || waitMaxTicks < waitMinTicks || hookedMinTicks < 0 || hookedMaxTicks < hookedMinTicks) {
            throw new IllegalArgumentException("Время рыбалки " + waitMinTicks + "…" + waitMaxTicks + " и поклёвки "
                    + hookedMinTicks + "…" + hookedMaxTicks + ": границы не отрицательные, максимум не меньше минимума");
        }
    }

    public static FishingWait parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "wait.json");
        try {
            return new FishingWait(JsonResource.integer(root.get("waitMinTicks"), origin, "waitMinTicks"),
                    JsonResource.integer(root.get("waitMaxTicks"), origin, "waitMaxTicks"),
                    JsonResource.integer(root.get("hookedMinTicks"), origin, "hookedMinTicks"),
                    JsonResource.integer(root.get("hookedMaxTicks"), origin, "hookedMaxTicks"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static FishingWait load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
