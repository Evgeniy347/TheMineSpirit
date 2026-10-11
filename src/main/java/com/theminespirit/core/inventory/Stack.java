package com.theminespirit.core.inventory;

import java.util.Objects;

public record Stack(String item, int variant, int count, int maxStack) {
    public Stack {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Предмет стопки не назван: без id не посчитать ценность и категорию");
        }
        if (variant < 0) {
            throw new IllegalArgumentException("Номер класса эквивалентности " + variant
                    + " отрицательный: вариант нумеруется с нуля, отрицательный номера не существует");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Стопка предмета '" + item + "' содержит " + count
                    + " предметов: пустая стопка заняла бы слот, ничего не храня");
        }
        if (maxStack <= 0) {
            throw new IllegalArgumentException("Максимум стопки предмета '" + item + "' равен " + maxStack
                    + ": слот обязан вмещать хотя бы один предмет");
        }
    }

    public boolean sameKindAs(Stack other) {
        Objects.requireNonNull(other, "Стопка не может быть null: сравнивают стопку со стопкой");
        return item.equals(other.item()) && variant == other.variant();
    }

    public int freeSpace() {
        return maxStack - count;
    }

    public Stack withCount(int count) {
        return new Stack(item, variant, count, maxStack);
    }
}
