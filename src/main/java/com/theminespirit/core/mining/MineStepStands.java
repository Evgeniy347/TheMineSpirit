package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class MineStepStands {
    public static final int MAX_STANDS = 6;

    public record Choice(List<BlockCoord> stands, String reason) {
        public Choice {
            stands = List.copyOf(
                    Objects.requireNonNull(stands, "Стоянки не могут быть null: пустой список — стоянки нет"));
            Objects.requireNonNull(reason, "Причина не может быть null: без неё дух не скажет, почему не подошёл");
        }

        public boolean found() {
            return !stands.isEmpty();
        }
    }

    private MineStepStands() {}

    public static Choice of(MineColumn column, @Nullable BlockCoord parentStand, BlockCoord from, SafetyView view) {
        Objects.requireNonNull(column, "Колонка не может быть null: без неё неизвестно, к какой ступени идти");
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней сортируются стоянки");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется стоянка и видимость");
        LinkedHashSet<BlockCoord> stands = new LinkedHashSet<>();
        if (parentStand != null && !shaft(column, parentStand) && MiningSafety.canStand(parentStand, view)) {
            stands.add(parentStand);
        }
        for (BlockCoord stand : StandSpots.of(column.stand(), view, from)) {
            if (shaft(column, stand)) {
                continue;
            }
            stands.add(stand);
            if (stands.size() >= MAX_STANDS) {
                break;
            }
        }
        if (stands.isEmpty()) {
            return new Choice(List.of(), reason(column, parentStand, view));
        }
        return new Choice(new ArrayList<>(stands), "");
    }

    public static boolean shaft(MineColumn column, BlockCoord at) {
        Objects.requireNonNull(column, "Колонка не может быть null: по ней проверяется клетка прохода");
        Objects.requireNonNull(at, "Клетка не может быть null: её проверяют на принадлежность проходу");
        BlockCoord floor = column.floor();
        return at.x() == floor.x() && at.z() == floor.z();
    }

    public static boolean visibleFrom(MineColumn column, BlockCoord feet, SafetyView view) {
        Objects.requireNonNull(column, "Колонка не может быть null: без неё неизвестно, что видно ломать");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: с них проверяется видимость клетки");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется проход луча");
        List<BlockCoord> cells = column.cells();
        for (int index = cells.size() - 1; index >= 0; index--) {
            if (MineDigOrder.visible(feet, cells.get(index), List.of(), view)) {
                return true;
            }
        }
        return false;
    }

    private static String reason(MineColumn column, @Nullable BlockCoord parentStand, SafetyView view) {
        BlockCoord stand = column.stand();
        if (!MiningSafety.canStand(stand, view)) {
            return "клетка ступени " + stand
                    + " не годится для стоянки: над ней нет прохода или под ней нет пола — вход завален";
        }
        if (parentStand != null) {
            return "ступень выше " + parentStand + " не годится для стоянки, а вокруг ступени " + stand
                    + " нет свободной клетки с прямой видимостью: преграда у входа";
        }
        return "у входа нет свободной клетки с прямой видимостью на ступень " + stand + ": вход завален или загорожен";
    }
}
