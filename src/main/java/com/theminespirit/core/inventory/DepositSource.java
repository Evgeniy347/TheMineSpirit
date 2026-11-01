package com.theminespirit.core.inventory;

import java.util.Objects;

public final class DepositSource {
    public record Decision(boolean transfer, boolean success, String reason) {
        public Decision {
            Objects.requireNonNull(reason, "Причина решения не может быть null: пустую задают пустой строкой");
            if (transfer && (success || !reason.isEmpty())) {
                throw new IllegalArgumentException("Перенос с готовым решением: перенос либо идёт, либо уже закрыт");
            }
            if (!transfer && !success && reason.isBlank()) {
                throw new IllegalArgumentException("Отказ без причины: по пустой строке не понять, чего не хватает");
            }
        }
    }

    private DepositSource() {}

    public static Decision of(int available, boolean leftover, String item) {
        Objects.requireNonNull(item, "Предмет не может быть null: без него непонятно, что сдавать");
        if (available < 0) {
            throw new IllegalArgumentException("Совпало " + available
                    + " предметов: отрицательное количество не бывает, ошибка в подсчёте инвентаря");
        }
        if (available > 0) {
            return new Decision(true, false, "");
        }
        if (leftover) {
            return new Decision(false, true, "");
        }
        return new Decision(
                false,
                false,
                "в инвентаре духа нет ни одного '" + item
                        + "': сдавать нечего, предмет нужно сначала добыть или получить передачей");
    }
}
