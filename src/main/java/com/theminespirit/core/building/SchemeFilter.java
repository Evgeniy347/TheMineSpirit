package com.theminespirit.core.building;

import java.util.List;
import java.util.Objects;

public final class SchemeFilter {
    private SchemeFilter() {}

    public static Schematic only(Schematic schematic, List<String> blocks) {
        Objects.requireNonNull(schematic, "Схема не может быть null: отбирать блоки не из чего");
        Objects.requireNonNull(blocks, "Отбор блоков не может быть null: все блоки — пустой список");
        if (blocks.isEmpty()) {
            return schematic;
        }
        List<String> paths = blocks.stream().map(SchematicBlocks::path).toList();
        List<Schematic.Entry> kept = schematic.blocks().stream()
                .filter(entry -> paths.contains(SchematicBlocks.path(entry.block())))
                .toList();
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("В схеме нет блоков " + blocks
                    + ": отбор стройки по схеме пуст — проверьте имена блоков отбора и легенду схемы");
        }
        return new Schematic(
                schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), kept, List.of(), schematic.repeat());
    }
}
