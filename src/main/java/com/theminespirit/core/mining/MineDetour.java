package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Обход колонки, в которой оставлена руда (решение владельца 22.10): параллельный проход сбоку — справа, иначе
 * слева. Колонки обхода повторяют полы исходного пути (родитель → оставленная → следующая), поэтому шаг по высоте
 * не больше блока и дух выходит назад тем же путём; родитель и следующая соединены с обходом шагом вбок.
 */
public final class MineDetour {
    private MineDetour() {}

    // Колонки обхода или пусто, если обе стороны упираются в клетку, которую копать нельзя (снова руда на удачу,
    // неразрушимый блок). forward — колонка плана за оставленной; null — оставленная была последней. /
    public static Optional<List<MineColumn>> around(
            MineColumn parent, MineColumn blocked, @Nullable MineColumn forward, Predicate<BlockCoord> undiggable) {
        Objects.requireNonNull(parent, "Родитель не может быть null: обход начинается из открытой колонки");
        Objects.requireNonNull(blocked, "Оставленная колонка не может быть null: обходить нечего");
        Objects.requireNonNull(undiggable, "Проверка клеток не может быть null: без неё обход упрётся в ту же руду");
        if (!parent.walksTo(blocked)) {
            throw new IllegalArgumentException("Колонка " + blocked.floor() + " не соседняя с родителем "
                    + parent.floor() + ": обход строится от колонки, с которой дух копает");
        }
        int dx = blocked.floor().x() - parent.floor().x();
        int dz = blocked.floor().z() - parent.floor().z();
        int[][] sides = {{dz, -dx}, {-dz, dx}};
        for (int[] side : sides) {
            List<MineColumn> detour = new ArrayList<>(3);
            detour.add(parent.shifted(side[0], side[1]));
            detour.add(blocked.shifted(side[0], side[1]));
            if (forward != null) {
                detour.add(forward.shifted(side[0], side[1]));
            }
            if (detour.stream().flatMap(column -> column.cells().stream()).noneMatch(undiggable)) {
                return Optional.of(List.copyOf(detour));
            }
        }
        return Optional.empty();
    }
}
