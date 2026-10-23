package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Порода дерева постройки: схемы рисуются дубом, а строят из породы, которая есть у поселения (джунгли, ель…).
 * Блок {@code oak_*} схемы заменяется на тот же блок породы; камень и прочее не трогаются. Порода поселения —
 * с наибольшим запасом брёвен, при равенстве или без брёвен — дуб.
 */
public final class WoodSpecies {
    public static final String OAK = "oak";
    public static final List<String> ALL =
            List.of(OAK, "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
    private static final String NAMESPACE = "minecraft:";
    private static final String OAK_PREFIX = "oak_";
    private static final String LOG_SUFFIX = "_log";

    private WoodSpecies() {}

    public static String check(String species) {
        Objects.requireNonNull(species, "Порода не может быть null: без неё строят по схеме");
        if (!ALL.contains(species)) {
            throw new IllegalArgumentException("Порода '" + species + "' неизвестна: годятся " + ALL);
        }
        return species;
    }

    public static String swap(String block, String species) {
        Objects.requireNonNull(block, "Блок не может быть null: заменять нечего");
        check(species);
        boolean namespaced = block.startsWith(NAMESPACE);
        String name = namespaced ? block.substring(NAMESPACE.length()) : block;
        if (OAK.equals(species) || !name.startsWith(OAK_PREFIX)) {
            return block;
        }
        String swapped = species + "_" + name.substring(OAK_PREFIX.length());
        return namespaced ? NAMESPACE + swapped : swapped;
    }

    public static Schematic swap(Schematic schematic, String species) {
        Objects.requireNonNull(schematic, "Схема не может быть null: заменять нечего");
        check(species);
        if (OAK.equals(species)) {
            return schematic;
        }
        List<Schematic.Entry> blocks = new ArrayList<>(schematic.blocks().size());
        for (Schematic.Entry entry : schematic.blocks()) {
            blocks.add(new Schematic.Entry(
                    entry.dx(),
                    entry.dy(),
                    entry.dz(),
                    swap(entry.block(), species),
                    entry.state(),
                    entry.text(),
                    entry.optional()));
        }
        return new Schematic(
                schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), blocks, schematic.air(), schematic.repeat());
    }

    public static String of(Map<String, Integer> stock) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        String best = OAK;
        int bestCount = logs(stock, OAK);
        for (String species : ALL) {
            int count = logs(stock, species);
            if (count > bestCount) {
                best = species;
                bestCount = count;
            }
        }
        return best;
    }

    public static boolean hasLogs(Map<String, Integer> stock) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        return ALL.stream().anyMatch(species -> logs(stock, species) > 0);
    }

    private static int logs(Map<String, Integer> stock, String species) {
        String log = species + LOG_SUFFIX;
        return stock.getOrDefault(log, 0) + stock.getOrDefault(NAMESPACE + log, 0);
    }
}
