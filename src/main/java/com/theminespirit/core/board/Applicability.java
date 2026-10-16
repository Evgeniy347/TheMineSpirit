package com.theminespirit.core.board;

import java.util.Objects;

/**
 * Применимость постоянного задания к духу: чистая функция по снимку состояния.
 * EAT_TO_FULL берёт дух, которому не хватает сытости или здоровья, вне боя, вне похода
 * и без занятости досковым заданием. RESTOCK_FOOD ждёт, пока личной еды меньше половины
 * стака. Остальные постоянные проверяет гейт ресурсов доски ({@code Board.claimable}).
 */
public final class Applicability {
    public static final int RESTOCK_FOOD_THRESHOLD = 32;

    private Applicability() {
    }

    public static boolean applicable(BoardCase boardCase, EatNeed eat, SpiritView spirit) {
        Objects.requireNonNull(boardCase, "Кейс применимости не может быть null: без него непонятно, "
                + "чьё постоянное задание проверяем");
        Objects.requireNonNull(eat, "Сытость духа не может быть null: нужду считают по ней");
        Objects.requireNonNull(spirit, "Состояние духа не может быть null: применимость считают по нему");
        if (boardCase == BoardCase.EAT_TO_FULL) {
            return (eat.needsFood() || eat.needsHealing()) && !spirit.busy();
        }
        if (boardCase == BoardCase.RESTOCK_FOOD) {
            return eat.food() < RESTOCK_FOOD_THRESHOLD && !spirit.boardBusy();
        }
        return true;
    }
}
