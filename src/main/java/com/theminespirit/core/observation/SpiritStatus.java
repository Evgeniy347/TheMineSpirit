package com.theminespirit.core.observation;

import org.jspecify.annotations.Nullable;

public record SpiritStatus(
        @Nullable HeldItem held,
        @Nullable Double health,
        @Nullable Double maxHealth,
        @Nullable Double food,
        @Nullable BoardTaskRef boardTask) {
    public SpiritStatus {
        if (health != null && health < 0) {
            throw new IllegalArgumentException(
                    "Здоровье " + health + " отрицательное: дух не бывает здоров отрицательно");
        }
        if (maxHealth != null && maxHealth <= 0) {
            throw new IllegalArgumentException(
                    "Максимум здоровья " + maxHealth + " не положителен: у духа всегда есть хотя бы одно сердце");
        }
        if (food != null && (food < 0 || food > 1)) {
            throw new IllegalArgumentException("Сытость " + food + " вне [0; 1]: шкала голода измеряется долей");
        }
    }

    public static SpiritStatus of(HeldItem held, double health, double maxHealth, double food, BoardTaskRef boardTask) {
        return new SpiritStatus(held, health, maxHealth, food, boardTask);
    }
}
