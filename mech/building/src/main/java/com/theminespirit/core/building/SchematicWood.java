package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SchematicWood {
    private SchematicWood() {}

    public static Schematic swap(Schematic schematic, String species) {
        Objects.requireNonNull(schematic, "Схема не может быть null: заменять нечего");
        WoodSpecies.check(species);
        if (WoodSpecies.OAK.equals(species)) {
            return schematic;
        }
        List<Schematic.Entry> blocks = new ArrayList<>(schematic.blocks().size());
        for (Schematic.Entry entry : schematic.blocks()) {
            blocks.add(new Schematic.Entry(
                    entry.dx(),
                    entry.dy(),
                    entry.dz(),
                    WoodSpecies.swap(entry.block(), species),
                    entry.state(),
                    entry.text(),
                    entry.optional()));
        }
        return new Schematic(
                schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), blocks, schematic.air(), schematic.repeat());
    }
}
