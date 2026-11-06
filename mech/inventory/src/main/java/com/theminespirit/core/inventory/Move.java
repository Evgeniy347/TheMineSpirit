package com.theminespirit.core.inventory;

public record Move(int fromSlot, int toSlot, int count) {
    public Move {
        if (fromSlot < 0) {
            throw new IllegalArgumentException("Слот источника " + fromSlot
                    + " отрицательный: слоты нумеруются с нуля, отрицательного слота не существует");
        }
        if (toSlot < 0) {
            throw new IllegalArgumentException(
                    "Слот приёмника " + toSlot + " отрицательный: слоты контейнера нумеруются с нуля");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Перемещение из слота " + fromSlot + " в слот " + toSlot + " несёт "
                    + count + " предметов: перемещение без предметов занимает ход и не делает ничего");
        }
    }
}
