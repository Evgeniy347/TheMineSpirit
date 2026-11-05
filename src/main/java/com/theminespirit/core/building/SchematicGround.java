package com.theminespirit.core.building;

import java.util.Objects;

public final class SchematicGround {
    private SchematicGround() {}

    public static boolean groundLayer(Schematic schematic) {
        Objects.requireNonNull(schematic, "Схема не может быть null: без неё неизвестно, лежит ли низ на земле");
        boolean ground = false;
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dy() != 0 || entry.optional()) {
                continue;
            }
            if (!SchematicActions.isActionPlaced(entry.block()) && !SchematicActions.isFluidPlaced(entry.block())) {
                return false;
            }
            ground = true;
        }
        return ground;
    }
}
