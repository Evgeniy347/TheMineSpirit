package com.theminespirit.core.board.graph;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record HerdCap(@Nullable Integer fixed, @Nullable Double perSpirit) {
    public HerdCap {
        if (fixed == null && perSpirit == null) {
            throw new IllegalArgumentException("Лимит стада без числа и без доли на духа: нечего сравнивать"
                    + " с поголовьем, задайте fixed или perSpirit");
        }
        if (fixed != null && perSpirit != null) {
            throw new IllegalArgumentException("Лимит стада задан и числом, и долей на духа: два предела"
                    + " молча перекрыли бы друг друга, оставьте один");
        }
        if (fixed != null && fixed < 0) {
            throw new IllegalArgumentException(
                    "Лимит стада " + fixed + " отрицательный: поголовье не бывает" + " отрицательным");
        }
        if (perSpirit != null && perSpirit < 0) {
            throw new IllegalArgumentException(
                    "Доля стада на духа " + perSpirit + " отрицательная: лимит" + " не может быть минусовым");
        }
    }

    public int limit(int spirits) {
        if (spirits < 0) {
            throw new IllegalArgumentException(
                    "Духов " + spirits + " отрицательное число: лимит поголовья" + " считается от их числа");
        }
        if (perSpirit != null) {
            return (int) Math.floor(perSpirit * spirits);
        }
        return Objects.requireNonNull(fixed, "У лимита стада нет ни числа, ни доли: проверьте разбор данных");
    }
}
