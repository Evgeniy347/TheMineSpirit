package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

public record DiffItem(BlockCoord cell, Action action, String found, Wanted wanted, SourceRef source, Access access) {
    public enum Action {
        BREAK(0),
        FIX_STATE(1),
        PLACE(2);

        private final int rank;

        Action(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    public static final Comparator<DiffItem> WORK_ORDER = Comparator.comparingInt(
                    (DiffItem item) -> item.action().rank())
            .thenComparingInt(item -> item.cell().y())
            .thenComparingInt(item -> item.cell().x())
            .thenComparingInt(item -> item.cell().z());

    public DiffItem {
        Objects.requireNonNull(cell, "Клетка предмета не может быть null: без адреса работать негде");
        Objects.requireNonNull(action, "Действие предмета не может быть null: дух обязан знать, что делать");
        Objects.requireNonNull(found, "Стоящий блок не может быть null: пустота приходит как air");
        Objects.requireNonNull(wanted, "Ожидание предмета не может быть null: иначе нечего устанавливать");
        Objects.requireNonNull(source, "Источник предмета не может быть null: по нему группа находит предмет");
        Objects.requireNonNull(access, "Право предмета не может быть null: иначе чужое сломать можно");
    }

    public static DiffItem breakAt(BlockCoord cell, String found, SourceRef source, Access access) {
        return new DiffItem(cell, Action.BREAK, found, Wanted.air(), source, access);
    }

    public static DiffItem place(BlockCoord cell, String found, Wanted wanted, SourceRef source, Access access) {
        return new DiffItem(cell, Action.PLACE, found, wanted, source, access);
    }

    public static DiffItem fixState(BlockCoord cell, String found, Wanted wanted, SourceRef source, Access access) {
        return new DiffItem(cell, Action.FIX_STATE, found, wanted, source, access);
    }

    public boolean satisfiedBy(String actual) {
        return BlockEquivalence.check(wanted, actual) == BlockEquivalence.Verdict.DONE;
    }

    public String text() {
        return cell + " " + action.name().toLowerCase(Locale.ROOT) + ": стоит " + found + ", надо " + wanted.text()
                + " (" + source.text() + ", " + access.text() + ")";
    }
}
