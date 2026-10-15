package com.theminespirit.core.armor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ArmorChoice {
    private ArmorChoice() {
    }

    public static Map<ArmorSlot, ArmorPiece> decide(List<ArmorPiece> inventory, Map<ArmorSlot, ArmorPiece> worn,
            ArmorTable table) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: выбор идёт по его содержимому");
        Objects.requireNonNull(worn, "Надетое не может быть null: пустое надевание задаётся пустой картой");
        Objects.requireNonNull(table, "Таблица материалов не может быть null: по ней разрешается ничья");
        Map<ArmorSlot, ArmorPiece> best = new LinkedHashMap<>();
        for (ArmorSlot slot : ArmorSlot.values()) {
            ArmorPiece candidate = bestInSlot(inventory, slot, table);
            ArmorPiece current = worn.get(slot);
            ArmorPiece chosen = pick(current, candidate, table);
            if (chosen != null) {
                best.put(slot, chosen);
            }
        }
        return best;
    }

    public static boolean isStrictlyBetter(ArmorPiece candidate, ArmorPiece worn, ArmorTable table) {
        Objects.requireNonNull(candidate, "Кандидат не может быть null: сравнение идёт с надетым предметом");
        Objects.requireNonNull(table, "Таблица материалов не может быть null: по ней разрешается ничья");
        if (worn == null) {
            return true;
        }
        return compare(candidate, worn, table) > 0;
    }

    public static int compare(ArmorPiece left, ArmorPiece right, ArmorTable table) {
        Objects.requireNonNull(left, "Левый предмет не может быть null: сравнивают два предмета брони");
        Objects.requireNonNull(right, "Правый предмет не может быть null: сравнивают два предмета брони");
        Objects.requireNonNull(table, "Таблица материалов не может быть null: по ней разрешается ничья");
        if (left.slot() != right.slot()) {
            throw new IllegalArgumentException("Предметы разных слотов (" + left.slot() + " и " + right.slot()
                    + ") несравнимы: броня надевается только в свой слот");
        }
        boolean leftBroken = isNearlyBroken(left, table);
        boolean rightBroken = isNearlyBroken(right, table);
        if (leftBroken != rightBroken) {
            return leftBroken ? -1 : 1;
        }
        int byDefense = Integer.compare(left.defense(), right.defense());
        if (byDefense != 0) {
            return byDefense;
        }
        int byToughness = Double.compare(left.toughness(), right.toughness());
        if (byToughness != 0) {
            return byToughness;
        }
        int byProtection = Integer.compare(left.protection(), right.protection());
        if (byProtection != 0) {
            return byProtection;
        }
        int byMaterial = Integer.compare(table.materialRank(left.item()), table.materialRank(right.item()));
        if (byMaterial != 0) {
            return byMaterial;
        }
        int byDurability = Double.compare(left.durabilityFraction(), right.durabilityFraction());
        if (byDurability != 0) {
            return byDurability;
        }
        return left.item().compareTo(right.item());
    }

    private static ArmorPiece pick(ArmorPiece worn, ArmorPiece candidate, ArmorTable table) {
        if (candidate == null) {
            return worn;
        }
        if (worn == null || compare(candidate, worn, table) > 0) {
            return candidate;
        }
        return worn;
    }

    private static ArmorPiece bestInSlot(List<ArmorPiece> inventory, ArmorSlot slot, ArmorTable table) {
        ArmorPiece best = null;
        for (ArmorPiece piece : inventory) {
            if (piece == null || piece.slot() != slot) {
                continue;
            }
            if (best == null || compare(piece, best, table) > 0) {
                best = piece;
            }
        }
        return best;
    }

    private static boolean isNearlyBroken(ArmorPiece piece, ArmorTable table) {
        return piece.durabilityFraction() < table.wornFraction();
    }

    public static List<ArmorPiece> inventoryOf(List<ArmorPiece> pieces, ArmorSlot slot) {
        List<ArmorPiece> result = new ArrayList<>();
        for (ArmorPiece piece : pieces) {
            if (piece != null && piece.slot() == slot) {
                result.add(piece);
            }
        }
        return result;
    }
}