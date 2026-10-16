package com.theminespirit.core.intellect;

public record StockLots(int foodItems, double foodNutrition, int ore, int iron, int weapons, int seeds) {
    public StockLots {
        if (foodItems < 0) {
            throw new IllegalArgumentException(
                    "Еды = " + foodItems + ": запас предметов не может быть отрицательным");
        }
        if (Double.isNaN(foodNutrition) || Double.isInfinite(foodNutrition) || foodNutrition < 0) {
            throw new IllegalArgumentException(
                    "Сытности = " + foodNutrition + ": запас обязан быть конечным и >= 0");
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
        if (seeds < 0) {
            throw new IllegalArgumentException("Семян = " + seeds + ": запас не может быть отрицательным");
        }
    }
}
