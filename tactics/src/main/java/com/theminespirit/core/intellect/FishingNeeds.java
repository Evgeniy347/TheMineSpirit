package com.theminespirit.core.intellect;

import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.processing.CookableFoods;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class FishingNeeds {
    private FishingNeeds() {}

    public static int cookableFish(Map<String, Integer> available) {
        if (available == null) {
            throw new IllegalArgumentException("Опись склада без карты не читается: пустой склад задают пустой картой");
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : available.entrySet()) {
            Integer count = entry.getValue();
            if (count != null && count > 0 && CookableFoods.isCookableFish(normalize(entry.getKey()))) {
                total += count;
            }
        }
        return total;
    }

    public static boolean wantsSmoker(Map<String, Integer> available, FishingData data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Порог коптильни без данных рыбалки не проверяется: число живёт в fishing.json");
        }
        return cookableFish(available) >= data.smokerFish();
    }

    public static boolean wantsFishing(double foodShare) {
        if (!Double.isFinite(foodShare) || foodShare < 0) {
            throw new IllegalArgumentException("Доля нехватки еды " + foodShare
                    + ": ожидалась конечная неотрицательная величина, иначе гейт рыбалки не определён");
        }
        return foodShare > 0;
    }

    public static double foodShare(Map<String, Integer> available, int population) {
        if (available == null) {
            throw new IllegalArgumentException("Опись склада без карты не читается: пустой склад задают пустой картой");
        }
        if (population < 0) {
            throw new IllegalArgumentException(
                    "Население " + population + ": норма еды считается на душу, душ не может быть отрицательно");
        }
        NutritionTable table = StockCatalog.defaultTable();
        double have = 0;
        for (Map.Entry<String, Integer> entry : available.entrySet()) {
            Integer count = entry.getValue();
            Double nutrition = table.nutrition().get(normalize(entry.getKey()));
            if (count != null && count > 0 && nutrition != null) {
                have += count * nutrition;
            }
        }
        IntellectTuning tuning = IntellectTuning.loadDefault();
        FoodDeficit deficit =
                FoodNorm.deficit(population, 0, tuning.foodDays(), tuning.foodRation(), tuning.foodChildRation(), have);
        return deficit.share();
    }

    private static String normalize(@Nullable String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        return item.indexOf(':') < 0 ? "minecraft:" + item : item;
    }
}
