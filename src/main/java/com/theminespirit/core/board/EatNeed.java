package com.theminespirit.core.board;

public record EatNeed(int food, int health, int maxFood, int maxHealth) {

    public EatNeed {
        if (maxFood < 1) {
            throw new IllegalArgumentException(
                    "Предел сытости " + maxFood + " меньше 1: " + "без предела непонятно, сыт ли дух");
        }
        if (maxHealth < 1) {
            throw new IllegalArgumentException(
                    "Предел здоровья " + maxHealth + " меньше 1: " + "без предела непонятно, здоров ли дух");
        }
        if (food < 0 || health < 0) {
            throw new IllegalArgumentException("Сытость " + food + " и здоровье " + health
                    + " не бывают отрицательными: отрицательное значение прятало бы нужду");
        }
    }

    public boolean needsFood() {
        return food < maxFood;
    }

    public boolean needsHealing() {
        return health < maxHealth;
    }

    public boolean full() {
        return !needsFood() && !needsHealing();
    }
}
