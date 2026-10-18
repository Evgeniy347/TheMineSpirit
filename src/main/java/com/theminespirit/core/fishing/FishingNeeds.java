package com.theminespirit.core.fishing;

import com.theminespirit.core.intellect.FoodDeficit;
import com.theminespirit.core.intellect.FoodNorm;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.NutritionTable;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.processing.CookableFoods;
import java.util.Map;

/**
 * Гейты рыболовства: когда доска заказывает коптильню и когда дух идёт на рабочую точку.
 * Числа порогов приходят из {@link FishingData}, а не из кода.
 */
public final class FishingNeeds {
    private FishingNeeds() {
    }

    /** Годная к жарке рыба на складе: треска и лосось. Иглобрюх и тропическая в счёт не идут. */
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

    /** Коптильню крафтят, когда годной к жарке рыбы набралось не меньше порога из данных. */
    public static boolean wantsSmoker(Map<String, Integer> available, FishingData data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Порог коптильни без данных рыбалки не проверяется: число живёт в fishing.json");
        }
        return cookableFish(available) >= data.smokerFish();
    }

    /**
     * Рыбалка как постоянное занятие: идти на рабочую точку, пока есть нехватка еды.
     * Доля нехватки — из {@code FoodDemand.share}; ноль значит «еды хватает».
     */
    public static boolean wantsFishing(double foodShare) {
        if (!Double.isFinite(foodShare) || foodShare < 0) {
            throw new IllegalArgumentException("Доля нехватки еды " + foodShare
                    + ": ожидалась конечная неотрицательная величина, иначе гейт рыбалки не определён");
        }
        return foodShare > 0;
    }

    /**
     * Доля нехватки еды поселения по снимку склада: сколько сытности есть против нормы на души.
     * Сытность берётся из таблицы еды, норма — из настроек интеллекта; ноль значит «еды хватает».
     */
    public static double foodShare(Map<String, Integer> available, int population) {
        if (available == null) {
            throw new IllegalArgumentException("Опись склада без карты не читается: пустой склад задают пустой картой");
        }
        if (population < 0) {
            throw new IllegalArgumentException("Население " + population
                    + ": норма еды считается на душу, душ не может быть отрицательно");
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
        FoodDeficit deficit = FoodNorm.deficit(population, 0, tuning.foodDays(), tuning.foodRation(),
                tuning.foodChildRation(), have);
        return deficit.share();
    }

    private static String normalize(String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        return item.indexOf(':') < 0 ? "minecraft:" + item : item;
    }
}
