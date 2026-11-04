package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record AuditDiff(List<Loss> lost, int gained, List<BlockCoord> unknownCells) {
    public record Loss(BlockCoord cell, String expected, String standing) {
        public Loss {
            Objects.requireNonNull(cell, "Клетка потери не может быть null: без неё нечего показать");
            Objects.requireNonNull(expected, "Ожидаемый блок не может быть null: улику без него не проверить");
            Objects.requireNonNull(standing, "Стоящий блок не может быть null: причина потери обязана быть");
        }

        public String text() {
            return cell + ": ожидался " + expected + ", " + standing;
        }
    }

    public AuditDiff {
        lost = List.copyOf(lost);
        unknownCells = List.copyOf(unknownCells);
    }

    public static AuditDiff between(BuildGap.Gap before, BuildGap.Gap now, Function<BlockCoord, String> standing) {
        Objects.requireNonNull(before, "Прошлый аудит не может быть null: без него не с чем сравнивать");
        Objects.requireNonNull(now, "Текущий аудит не может быть null: сравнивают два замера");
        Objects.requireNonNull(standing, "Проба блока не может быть null: иначе причину потери не назвать");
        Set<BlockCoord> was = done(before);
        Set<BlockCoord> is = done(now);
        List<Loss> lost = new ArrayList<>();
        for (BlockCoord cell : was) {
            if (is.contains(cell)) {
                continue;
            }
            if (now.unknownCells().contains(cell)) {
                lost.add(new Loss(cell, now.missingCells().getOrDefault(cell, "?"), "чанк не загружен"));
                continue;
            }
            if (!now.cells().contains(cell)) {
                lost.add(new Loss(cell, "?", "клетки больше нет в плане"));
                continue;
            }
            lost.add(new Loss(cell, now.missingCells().getOrDefault(cell, "?"), "стоит " + standing.apply(cell)));
        }
        lost.sort(Comparator.comparingInt((Loss loss) -> loss.cell().y())
                .thenComparingInt(loss -> loss.cell().x())
                .thenComparingInt(loss -> loss.cell().z()));
        int gained = (int) is.stream().filter(cell -> !was.contains(cell)).count();
        Set<BlockCoord> wasUnknown = before.unknownCells();
        List<BlockCoord> unknown = now.unknownCells().stream()
                .filter(cell -> !wasUnknown.contains(cell))
                .sorted(Comparator.comparingInt(BlockCoord::y)
                        .thenComparingInt(BlockCoord::x)
                        .thenComparingInt(BlockCoord::z))
                .toList();
        return new AuditDiff(lost, gained, unknown);
    }

    public static Set<BlockCoord> done(BuildGap.Gap gap) {
        Objects.requireNonNull(gap, "Аудит не может быть null: готовность считается по его клеткам");
        return gap.cells().stream()
                .filter(cell -> !gap.missingCells().containsKey(cell))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean empty() {
        return lost.isEmpty() && gained == 0 && unknownCells.isEmpty();
    }
}
