package com.theminespirit.core.building;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Дешёвый материал укрытия первого дня (INC-21): булыжник и доски. Брёвна и обожжённый камень дороги
 * (из бревна четыре доски, гладкий камень — обжиг), поэтому в выбор не попадают. Порог входа — полублок того же
 * материала, чтобы в укрытие входили шагом, а не прыжком.
 */
public final class ShelterMaterials {
    private static final String COBBLESTONE = "cobblestone";
    private static final String COBBLESTONE_SLAB = "cobblestone_slab";
    private static final String PLANKS = "_planks";
    private static final String SLAB = "_slab";

    public record Choice(String block, String slab) {}

    private ShelterMaterials() {}

    // Порядок дешевизны: булыжник (лежит под ногами), потом доски. Минус — материал не годится.
    public static int rank(String blockId) {
        String path = SchematicBlocks.path(blockId);
        if (COBBLESTONE.equals(path)) {
            return 0;
        }
        return path.endsWith(PLANKS) ? 1 : -1;
    }

    public static String slabFor(String blockId) {
        String path = SchematicBlocks.path(blockId);
        if (COBBLESTONE.equals(path)) {
            return COBBLESTONE_SLAB;
        }
        if (!path.endsWith(PLANKS)) {
            throw new IllegalArgumentException("Материал укрытия '" + blockId + "' не дешёвый: полублок берётся "
                    + "только из булыжника или досок");
        }
        return path.substring(0, path.length() - PLANKS.length()) + SLAB;
    }

    // Материал с полным запасом корпуса (needed) и полублока для порога (slabNeeded); из равных — дешевле.
    // Полного запаса нет ни у кого — берём самый дешёвый из имеющихся, чтобы предпроверка назвала точные числа.
    public static Optional<Choice> choose(Map<String, Integer> counts, int needed, int slabNeeded) {
        Objects.requireNonNull(counts, "Запасы не могут быть null: без них материал не выбрать");
        if (needed < 1) {
            throw new IllegalArgumentException("Блоков укрытия " + needed + ": нужно хотя бы 1");
        }
        if (slabNeeded < 0) {
            throw new IllegalArgumentException("Полублоков порога " + slabNeeded + ": счётчик не отрицателен");
        }
        Choice complete = null;
        int completeRank = Integer.MAX_VALUE;
        Choice cheapest = null;
        int cheapestRank = Integer.MAX_VALUE;
        int cheapestCount = -1;
        for (Map.Entry<String, Integer> stock : counts.entrySet()) {
            int rank = rank(stock.getKey());
            if (rank < 0) {
                continue;
            }
            String slab = slabFor(stock.getKey());
            boolean enough = stock.getValue() >= needed && counts.getOrDefault(slab, 0) >= slabNeeded;
            if (enough && rank < completeRank) {
                complete = new Choice(stock.getKey(), slab);
                completeRank = rank;
            }
            if (rank < cheapestRank || (rank == cheapestRank && stock.getValue() > cheapestCount)) {
                cheapest = new Choice(stock.getKey(), slab);
                cheapestRank = rank;
                cheapestCount = stock.getValue();
            }
        }
        return Optional.ofNullable(complete != null ? complete : cheapest);
    }
}
