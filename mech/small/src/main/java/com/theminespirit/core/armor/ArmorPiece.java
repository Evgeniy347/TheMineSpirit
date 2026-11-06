package com.theminespirit.core.armor;

import java.util.Objects;

public record ArmorPiece(
        String item, ArmorSlot slot, int defense, double toughness, int protection, double durabilityFraction) {
    public ArmorPiece {
        Objects.requireNonNull(item, "Предмет брони не назван: без id его не вернуть в инвентарь");
        if (item.isBlank()) {
            throw new IllegalArgumentException("Предмет брони пуст: id задаётся строкой вида minecraft:iron_helmet");
        }
        Objects.requireNonNull(slot, "Слот брони не назван: без него неизвестно, что сравнивать");
        if (defense < 0) {
            throw new IllegalArgumentException("Очки брони предмета '" + item + "' равны " + defense
                    + ": отрицательная защита не имеет смысла и сломала бы сравнение");
        }
        if (toughness < 0) {
            throw new IllegalArgumentException("Крепость предмета '" + item + "' равна " + toughness
                    + ": отрицательная крепость не имеет смысла и сломала бы сравнение");
        }
        if (protection < 0) {
            throw new IllegalArgumentException("Уровень защиты предмета '" + item + "' равен " + protection
                    + ": отрицательное зачарование не имеет смысла и сломало бы сравнение");
        }
        if (durabilityFraction < 0 || durabilityFraction > 1) {
            throw new IllegalArgumentException("Доля прочности предмета '" + item + "' равна " + durabilityFraction
                    + ": доля задаётся от 0 (сломан) до 1 (целый), иначе порог «почти сломан» не работает");
        }
    }
}
