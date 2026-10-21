package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.inventory.InventoryFill;
import com.theminespirit.core.processing.ProcessingStock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SettlementView(
        int population,
        int children,
        double foodPerSoul,
        double foodNutritionPerSoul,
        int ore,
        int iron,
        int weapons,
        int threats,
        double dayFraction,
        FarmWork farmWork,
        Forage forage,
        List<SpiritSnapshot> spirits,
        long tick,
        ProcessingStock processing,
        Map<UUID, InventoryFill> inventoryFills,
        WorkStations stations) {
    public SettlementView {
        if (population < 0) {
            throw new IllegalArgumentException(
                    "Население = " + population + ": число духов не может быть отрицательным");
        }
        if (children < 0) {
            throw new IllegalArgumentException("Детей = " + children + ": число детей не может быть отрицательным");
        }
        if (children > population) {
            throw new IllegalArgumentException(
                    "Детей = " + children + " при населении " + population + ": детей не может быть больше, чем духов");
        }
        if (Double.isNaN(foodPerSoul) || Double.isInfinite(foodPerSoul) || foodPerSoul < 0) {
            throw new IllegalArgumentException("Еды на душу = " + foodPerSoul + ": запас обязан быть конечным и >= 0");
        }
        if (Double.isNaN(foodNutritionPerSoul) || Double.isInfinite(foodNutritionPerSoul) || foodNutritionPerSoul < 0) {
            throw new IllegalArgumentException(
                    "Сытности на душу = " + foodNutritionPerSoul + ": запас обязан быть конечным и >= 0");
        }
        if (ore < 0) {
            throw new IllegalArgumentException("Руды = " + ore + ": запас не может быть отрицательным");
        }
        if (iron < 0) {
            throw new IllegalArgumentException("Железа = " + iron + ": запас не может быть отрицательным");
        }
        if (weapons < 0) {
            throw new IllegalArgumentException("Оружия = " + weapons + ": запас не может быть отрицательным");
        }
        if (threats < 0) {
            throw new IllegalArgumentException("Угроз = " + threats + ": число угроз не может быть отрицательным");
        }
        if (Double.isNaN(dayFraction) || dayFraction < 0 || dayFraction > 1) {
            throw new IllegalArgumentException("Доля суток = " + dayFraction + ": время суток обязано лежать в 0..1");
        }
        if (farmWork == null) {
            throw new IllegalArgumentException("Работа фермы не может быть null: её задают FarmWork.NONE");
        }
        if (forage == null) {
            throw new IllegalArgumentException("Добыча еды не может быть null: её отсутствие задают Forage.NONE");
        }
        if (spirits == null) {
            throw new IllegalArgumentException(
                    "Список духов не может быть null: пустое поселение задают пустым списком");
        }
        for (int index = 1; index < spirits.size(); index++) {
            UUID prev = spirits.get(index - 1).id();
            UUID next = spirits.get(index).id();
            if (prev.compareTo(next) >= 0) {
                throw new IllegalArgumentException("Духи в снимке поселения не упорядочены по id: "
                        + "распределение обязано быть детерминированным и не зависеть от порядка сбора");
            }
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Тик = " + tick + ": время снимка не может быть отрицательным");
        }
        if (processing == null) {
            throw new IllegalArgumentException(
                    "Запас переработки не может быть null: его отсутствие задают ProcessingStock.NONE");
        }
        if (inventoryFills == null) {
            throw new IllegalArgumentException(
                    "Заполнения инвентарей не могут быть null: " + "пустое задают пустой картой");
        }
        for (Map.Entry<UUID, InventoryFill> entry : inventoryFills.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Заполнение с null-духом или null-значением не принимается: "
                        + "заполнение привязано к конкретному духу");
            }
        }
        if (stations == null) {
            throw new IllegalArgumentException(
                    "Станции поселения не могут быть null: их отсутствие задают WorkStations.NONE");
        }
        inventoryFills = Map.copyOf(inventoryFills);
        spirits = List.copyOf(spirits);
    }

    public SettlementView(
            int population,
            int children,
            double foodPerSoul,
            double foodNutritionPerSoul,
            int ore,
            int iron,
            int weapons,
            int threats,
            double dayFraction,
            FarmWork farmWork,
            Forage forage,
            List<SpiritSnapshot> spirits,
            long tick) {
        this(
                population,
                children,
                foodPerSoul,
                foodNutritionPerSoul,
                ore,
                iron,
                weapons,
                threats,
                dayFraction,
                farmWork,
                forage,
                spirits,
                tick,
                ProcessingStock.NONE,
                Map.of(),
                WorkStations.NONE);
    }

    public SettlementView(
            int population,
            int children,
            double foodPerSoul,
            double foodNutritionPerSoul,
            int ore,
            int iron,
            int weapons,
            int threats,
            double dayFraction,
            FarmWork farmWork,
            Forage forage,
            List<SpiritSnapshot> spirits,
            long tick,
            ProcessingStock processing) {
        this(
                population,
                children,
                foodPerSoul,
                foodNutritionPerSoul,
                ore,
                iron,
                weapons,
                threats,
                dayFraction,
                farmWork,
                forage,
                spirits,
                tick,
                processing,
                Map.of(),
                WorkStations.NONE);
    }

    public SettlementView(
            int population,
            int children,
            double foodPerSoul,
            double foodNutritionPerSoul,
            int ore,
            int iron,
            int weapons,
            int threats,
            double dayFraction,
            FarmWork farmWork,
            Forage forage,
            List<SpiritSnapshot> spirits,
            long tick,
            Map<UUID, InventoryFill> inventoryFills) {
        this(
                population,
                children,
                foodPerSoul,
                foodNutritionPerSoul,
                ore,
                iron,
                weapons,
                threats,
                dayFraction,
                farmWork,
                forage,
                spirits,
                tick,
                ProcessingStock.NONE,
                inventoryFills,
                WorkStations.NONE);
    }

    public SettlementView(
            int population,
            int children,
            double foodPerSoul,
            double foodNutritionPerSoul,
            int ore,
            int iron,
            int weapons,
            int threats,
            double dayFraction,
            FarmWork farmWork,
            Forage forage,
            List<SpiritSnapshot> spirits,
            long tick,
            ProcessingStock processing,
            Map<UUID, InventoryFill> inventoryFills) {
        this(
                population,
                children,
                foodPerSoul,
                foodNutritionPerSoul,
                ore,
                iron,
                weapons,
                threats,
                dayFraction,
                farmWork,
                forage,
                spirits,
                tick,
                processing,
                inventoryFills,
                WorkStations.NONE);
    }

    public Optional<InventoryFill> inventoryFill(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: заполнение ищут по нему");
        return Optional.ofNullable(inventoryFills.get(spirit));
    }

    public int adults() {
        return population - children;
    }

    public List<SpiritSnapshot> idle() {
        return spirits.stream().filter(spirit -> spirit.workTarget().isEmpty()).toList();
    }

    public List<SpiritSnapshot> freeWorkers() {
        return idle().stream()
                .filter(spirit -> spirit.role() == SpiritRole.WORKER || spirit.role() == SpiritRole.IDLE)
                .toList();
    }

    public FoodDeficit foodDeficit(double days, double adultRation, double childRation) {
        return FoodNorm.deficit(adults(), children, days, adultRation, childRation, foodNutritionPerSoul * population);
    }

    public boolean needsOre(int oreTarget, int ironTarget, int weaponTarget) {
        if (oreTarget < 0 || ironTarget < 0 || weaponTarget < 0) {
            throw new IllegalArgumentException("Цели запасов не могут быть отрицательными: руда " + oreTarget
                    + ", железо " + ironTarget + ", оружие " + weaponTarget);
        }
        return ore < oreTarget || iron < ironTarget || weapons < weaponTarget;
    }
}
