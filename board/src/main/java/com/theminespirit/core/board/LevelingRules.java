package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import com.theminespirit.core.reload.api.TableSlot;
import java.util.Set;
import java.util.function.Supplier;

public record LevelingRules(
        int surplusPercent, int batch, int chestKeep, int tripMin, int waterRadius, int waterMinBlocks) {
    public static final String RESOURCE = "data/theminespirit/board/leveling.json";
    private static final TableSlot<LevelingRules> SOURCE = new TableSlot<>(LevelingRules::load);
    private static final Set<String> FIELDS =
            Set.of("surplusPercent", "batch", "chestKeep", "tripMin", "waterRadius", "waterMinBlocks");

    public LevelingRules {
        if (surplusPercent < 0 || batch < 1 || chestKeep < 0 || tripMin < 0) {
            throw new IllegalArgumentException("Выравнивание: запас " + surplusPercent + "%, партия " + batch
                    + ", неприкосновенный запас сундуков " + chestKeep + ", ходка за землёй от " + tripMin
                    + ": запас, минимум и ходка не отрицательные, партия — хотя бы блок");
        }
        if (waterRadius < 1 || waterMinBlocks < 1) {
            throw new IllegalArgumentException("Выравнивание: радиус поиска воды " + waterRadius + ", минимум колонок "
                    + waterMinBlocks + ": радиус и минимум воды положительные");
        }
    }

    public boolean enough(int cut, int fill, int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException(
                    "Запас земли " + stock + " отрицательный: склад не может быть должником");
        }
        return 100L * ((long) cut + stock) >= (100L + surplusPercent) * fill;
    }

    public static LevelingRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "leveling.json");
        try {
            return new LevelingRules(
                    JsonResource.integer(root.get("surplusPercent"), origin, "surplusPercent"),
                    JsonResource.integer(root.get("batch"), origin, "batch"),
                    JsonResource.integer(root.get("chestKeep"), origin, "chestKeep"),
                    JsonResource.integer(root.get("tripMin"), origin, "tripMin"),
                    JsonResource.integer(root.get("waterRadius"), origin, "waterRadius"),
                    JsonResource.integer(root.get("waterMinBlocks"), origin, "waterMinBlocks"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static LevelingRules current() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<LevelingRules> supplier) {
        SOURCE.use(supplier);
    }

    public static LevelingRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
