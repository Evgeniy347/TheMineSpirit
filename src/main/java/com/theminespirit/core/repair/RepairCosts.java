package com.theminespirit.core.repair;

/**
 * Стоимость способов ремонта в одних единицах: один материал ремонта — {@link #MATERIAL_COST},
 * один уровень опыта — {@code experienceWeight}, целый второй инструмент — {@code toolCost}.
 * Числа решают, что дешевле; значения живут здесь, а не в ветке выбора.
 */
public record RepairCosts(int anvilXpCost, int experienceWeight, int toolCost) {
    public static final int MATERIAL_COST = 1;
    public static final int ANVIL_XP_COST = 1;
    public static final int EXPERIENCE_WEIGHT = 2;
    public static final int TOOL_COST = 10;

    public RepairCosts {
        if (anvilXpCost < 0) {
            throw new IllegalArgumentException("Стоимость опыта наковальни " + anvilXpCost
                    + " отрицательна: опыт не восполняет ресурс, а тратит его");
        }
        if (experienceWeight < 0) {
            throw new IllegalArgumentException("Вес опыта " + experienceWeight
                    + " отрицателен: уровень не может быть выгоден при выборе способа ремонта");
        }
        if (toolCost < 0) {
            throw new IllegalArgumentException("Стоимость второго инструмента " + toolCost
                    + " отрицательна: инструмент не восполняет ресурс, а тратит его");
        }
    }

    public static RepairCosts defaults() {
        return new RepairCosts(ANVIL_XP_COST, EXPERIENCE_WEIGHT, TOOL_COST);
    }

    /** Стоимость ремонта наковальней: материал плюс уровни опыта по весу. */
    public long anvilCost() {
        return MATERIAL_COST + (long) anvilXpCost * experienceWeight;
    }
}
