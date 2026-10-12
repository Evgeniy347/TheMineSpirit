package com.theminespirit.core.inventory;

public record Slice(int slot, int count) {
    public Slice {
        if (slot < 0) {
            throw new IllegalArgumentException("Номер слота " + slot
                    + " отрицательный: слоты инвентаря нумеруются с нуля, отрицательного слота не существует");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Излишек в слоте " + slot + " равен " + count
                    + ": нулевой излишек нечего отдавать в контейнер");
        }
    }
}
