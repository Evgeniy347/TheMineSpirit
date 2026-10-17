package com.theminespirit.core.planner;

import java.util.List;
import java.util.Optional;

public interface ResourceKnowledge {
    boolean isGatherable(String item);

    /**
     * Блок, который надо сломать, чтобы добыть предмет: по умолчанию сам предмет —
     * для дропов адаптер возвращает блок-источник из таблицы «дроп ← блок».
     */
    default String gatherBlockFor(String item) {
        return item;
    }

    boolean isCraftable(String item);

    int outputCount(String item);

    List<Ingredient> ingredientsFor(String item);

    boolean isSmeltable(String item);

    String smeltInputFor(String item);

    default Optional<String> stationFor(String item) {
        return Optional.empty();
    }

    /**
     * Минимальный инструмент, без которого сломанный блок не отдаёт дроп:
     * пусто — блок ломается рукой. Адаптер возвращает кирку по уровню инструмента
     * (камень — деревянная, руда — каменная и т. д.), чтобы резолвер поставил её
     * предварительным шагом.
     */
    default Optional<String> toolForBlock(String block) {
        return Optional.empty();
    }

    default int burnTicks(String item) {
        return FuelTable.burnTicks(item);
    }

    default List<String> fuelCandidates() {
        return FuelTable.preferenceOrder();
    }
}