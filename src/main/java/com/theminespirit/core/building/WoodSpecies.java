package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    // Порода деревянного предмета по префиксу имени (jungle_fence → jungle); не деревянный — пусто.
    public static Optional<String> speciesOf(String item) {
        Objects.requireNonNull(item, "Предмет не может быть null: породу берут из имени");
        String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
        return ALL.stream().filter(species -> name.startsWith(species + "_")).findFirst();
    }

    // Тот же деревянный блок другой породы (jungle_log и oak_log, jungle_fence и oak_fence): стройка такой не сносит —
    // поставленный поселением столб при смене породы считался «чужим» и ломался (INC-58).
    public static boolean sameRole(String first, String second) {
        Objects.requireNonNull(first, "Блок не может быть null: роль сравнивают по имени");
        Objects.requireNonNull(second, "Блок не может быть null: роль сравнивают по имени");
        Optional<String> left = speciesOf(first);
        Optional<String> right = speciesOf(second);
        return left.isPresent() && right.isPresent() && role(first, left.get()).equals(role(second, right.get()));
    }

    private static String role(String block, String species) {
        String name = block.startsWith(NAMESPACE) ? block.substring(NAMESPACE.length()) : block;
        return name.substring(species.length() + 1);
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

    // Брёвен рядом нет (вырублены за долгую игру или не попали в выборку) — порода по биому: дуб по умолчанию
    // ставил ограду из дуба в джунглях, gather:oak_log не добывался (INC-37, живой прогон 24.10).
    public static String ofBiome(String biome) {
        Objects.requireNonNull(biome, "Биом не может быть null: по нему выбирается порода");
        String path = biome.substring(biome.indexOf(':') + 1);
        if (path.contains("cherry")) {
            return "cherry";
        }
        if (path.contains("jungle")) {
            return "jungle";
        }
        if (path.contains("mangrove")) {
            return "mangrove";
        }
        if (path.contains("dark_forest")) {
            return "dark_oak";
        }
        if (path.contains("birch")) {
            return "birch";
        }
        if (path.contains("savanna")) {
            return "acacia";
        }
        if (path.contains("taiga") || path.contains("grove") || path.contains("snowy")) {
            return "spruce";
        }
        return OAK;
    }

    public static boolean hasLogs(Map<String, Integer> stock) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        return ALL.stream().anyMatch(species -> logs(stock, species) > 0);
    }

    // Вариант слота рецепта по запасу и породе: сначала тот, которого в запасе больше всего, затем вариант породы
    // поселения (вместо {@code oak_*}). Пусто — предпочтения нет, выбирает общий порядок (палки и сундук в джунглях
    // брали дубовые доски, которых не добыть).
    public static Optional<String> prefer(List<String> alternatives, Map<String, Integer> stock, String species) {
        Objects.requireNonNull(alternatives, "Варианты не могут быть null: выбирать не из чего");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        check(species);
        String best = null;
        int bestCount = 0;
        for (String alternative : alternatives) {
            int count = count(stock, alternative);
            if (count > bestCount) {
                best = alternative;
                bestCount = count;
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        if (OAK.equals(species)) {
            return Optional.empty();
        }
        for (String alternative : alternatives) {
            String swapped = swap(alternative, species);
            if (!swapped.equals(alternative) && alternatives.contains(swapped)) {
                return Optional.of(swapped);
            }
        }
        return Optional.empty();
    }

    private static int count(Map<String, Integer> stock, String item) {
        String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
        return stock.getOrDefault(name, 0) + stock.getOrDefault(NAMESPACE + name, 0);
    }

    private static int logs(Map<String, Integer> stock, String species) {
        String log = species + LOG_SUFFIX;
        return stock.getOrDefault(log, 0) + stock.getOrDefault(NAMESPACE + log, 0);
    }
}
