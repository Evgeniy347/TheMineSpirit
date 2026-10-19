package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.reload.DataTables;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Разведение, контроль численности и запас продуктов по видам. Условия — чистые функции
 * по снимку стада: загон построен и приманены → доступно разведение, пока лимит вида не
 * выбран; при выбранном лимите вместо разведения — контроль численности. Нормы продуктов
 * на душу — данные таблицы board ({@code products}), а не числа из головы.
 */
public final class BreedingRules {
    public enum Action {
        BREED,
        CULL,
        NONE
    }

    private BreedingRules() {}

    public static Action action(Species species, int housed, boolean penBuilt) {
        Objects.requireNonNull(
                species, "Вид разведения не может быть null: без него неизвестно, " + "чей загон и лимит считать");
        if (housed < 0) {
            throw new IllegalArgumentException("Поголовье " + housed + " отрицательное: минус голов "
                    + "прятал бы перебор и ломал переход к контролю численности");
        }
        if (!penBuilt) {
            return Action.NONE;
        }
        if (housed < HerdCap.minBreed()) {
            return Action.NONE;
        }
        if (housed >= HerdCap.maxPerSpecies()) {
            return species == Species.SHEEP ? Action.NONE : Action.CULL;
        }
        return Action.BREED;
    }

    public static boolean breedingAvailable(Species species, int housed, boolean penBuilt) {
        return action(species, housed, penBuilt) == Action.BREED;
    }

    public static boolean cullNeeded(Species species, int housed, boolean penBuilt) {
        return action(species, housed, penBuilt) == Action.CULL;
    }

    public static Map<String, Integer> breedingGate(Species species) {
        Objects.requireNonNull(species, "Вид гейта не может быть null: без него неизвестно, чем кормить пару");
        String feed = FeedTable.feedFor(species).get(0);
        return Map.of(feed, HerdCap.minBreed());
    }

    public static Map<String, Integer> stockPerCapita(Species species) {
        Objects.requireNonNull(species, "Вид нормы не может быть null: без него неизвестно, что считать");
        Map<String, Integer> perCapita = DataTables.INSTANCE
                .board()
                .current()
                .products()
                .get(species.name().toLowerCase(Locale.ROOT));
        return perCapita == null ? Map.of() : perCapita;
    }

    public static Map<String, Integer> stockFor(Species species, int population) {
        Objects.requireNonNull(species, "Вид запаса не может быть null: без него неизвестно, что считать");
        if (population < 0) {
            throw new IllegalArgumentException(
                    "Численность " + population + " отрицательная: запас на " + "минус голов не считается");
        }
        Map<String, Integer> stock = new LinkedHashMap<>();
        stockPerCapita(species).forEach((item, perCapita) -> stock.put(item, perCapita * population));
        return Map.copyOf(stock);
    }
}
