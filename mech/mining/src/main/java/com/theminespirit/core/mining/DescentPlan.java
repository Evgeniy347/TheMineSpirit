package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record DescentPlan(
        BlockCoord entry,
        DescentDirection direction,
        int targetY,
        int torchInterval,
        List<DescentStep> steps,
        DescentStop stop,
        String dangerReason) {

    public static final int WORLD_MIN_Y = -64;
    public static final int WORLD_MAX_Y = 319;

    public DescentPlan {
        Objects.requireNonNull(entry, "Точка входа не может быть null: без неё неизвестно, откуда спускаться");
        Objects.requireNonNull(direction, "Направление спуска не может быть null: без него неизвестно, куда копать");
        Objects.requireNonNull(steps, "Шаги спуска не могут быть null: пустой список — спуск уже закончен");
        Objects.requireNonNull(stop, "Причина остановки не может быть null: TARGET_Y или DANGER");
        Objects.requireNonNull(dangerReason, "Описание опасности не может быть null: пустая строка — опасности нет");
        if (targetY < WORLD_MIN_Y || targetY > WORLD_MAX_Y) {
            throw new IllegalArgumentException("Целевой этаж " + targetY + " вне мира [" + WORLD_MIN_Y + ", "
                    + WORLD_MAX_Y + "]: под дном мира копать некуда, а выше потолка сверлиться некуда");
        }
        if (torchInterval < 1) {
            throw new IllegalArgumentException("Интервал факелов " + torchInterval + " меньше единицы: "
                    + "без факелов шахта станет тёмной, интервал считается через шаги спуска");
        }
        steps = List.copyOf(steps);
        if (stop == DescentStop.DANGER && dangerReason.isBlank()) {
            throw new IllegalArgumentException("Остановка DANGER без описания опасности: игрок не поймёт, "
                    + "что остановило духа, назовите блок и клетку");
        }
        if (stop == DescentStop.TARGET_Y && !dangerReason.isBlank()) {
            throw new IllegalArgumentException("Остановка TARGET_Y с описанием '" + dangerReason + "': "
                    + "до цели дошли без опасности, лишнее описание запутывает отчёт скрипта");
        }
    }

    public static DescentPlan from(
            SafetyView view, BlockCoord entry, DescentDirection direction, int targetY, int torchInterval) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не проверить безопасность шагов");
        Objects.requireNonNull(entry, "Точка входа не может быть null: без неё неизвестно, откуда спускаться");
        Objects.requireNonNull(direction, "Направление спуска не может быть null: без него неизвестно, куда копать");
        if (entry.y() < WORLD_MIN_Y || entry.y() > WORLD_MAX_Y) {
            throw new IllegalArgumentException("Точка входа " + entry + " вне мира по высоте [" + WORLD_MIN_Y + ", "
                    + WORLD_MAX_Y + "]: спуск начинается от ног духа в пределах мира");
        }
        if (targetY < WORLD_MIN_Y || targetY > WORLD_MAX_Y) {
            throw new IllegalArgumentException("Целевой этаж " + targetY + " вне мира [" + WORLD_MIN_Y + ", "
                    + WORLD_MAX_Y + "]: под дном мира копать некуда, а выше потолка сверлиться некуда");
        }
        if (torchInterval < 1) {
            throw new IllegalArgumentException("Интервал факелов " + torchInterval + " меньше единицы: "
                    + "без факелов шахта станет тёмной, интервал считается через шаги спуска");
        }
        int entryFloor = entry.y() - 1;
        List<DescentStep> steps = new ArrayList<>();
        String dangerReason = null;
        int index = 0;
        for (int floor = entryFloor; floor >= targetY; floor--, index++) {
            int columnX = entry.x() + index * direction.dx;
            int columnZ = entry.z() + index * direction.dz;
            BlockCoord stand = new BlockCoord(columnX, floor + 1, columnZ);
            List<BlockCoord> passage = List.of(
                    new BlockCoord(columnX, floor + 1, columnZ),
                    new BlockCoord(columnX, floor + 2, columnZ),
                    new BlockCoord(columnX, floor + 3, columnZ));
            DescentStep step = new DescentStep(index, floor, stand, passage);
            String danger = dangerAt(view, step, direction);
            if (danger != null) {
                dangerReason = danger;
                break;
            }
            steps.add(step);
        }
        DescentStop stop = dangerReason == null ? DescentStop.TARGET_Y : DescentStop.DANGER;
        return new DescentPlan(
                entry, direction, targetY, torchInterval, steps, stop, dangerReason == null ? "" : dangerReason);
    }

    public static @Nullable String dangerAt(SafetyView view, DescentStep step, DescentDirection direction) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не проверить безопасность шага");
        Objects.requireNonNull(step, "Шаг спуска не может быть null: опасность проверяется у конкретного шага");
        Objects.requireNonNull(direction, "Направление спуска не может быть null: от него зависят боковые клетки");
        int floor = step.floor();
        BlockCoord stand = step.stand();
        String floorDanger =
                DescentDanger.solidFloor(view, new BlockCoord(stand.x(), floor, stand.z()), "пол", step.index());
        if (floorDanger != null) {
            return floorDanger;
        }
        String subFloorDanger =
                DescentDanger.solidFloor(view, new BlockCoord(stand.x(), floor - 1, stand.z()), "подпол", step.index());
        if (subFloorDanger != null) {
            return subFloorDanger;
        }
        int sideX = -direction.dz;
        int sideZ = direction.dx;
        for (BlockCoord cell : step.passage()) {
            String forward = DescentDanger.fluidPassage(
                    view, new BlockCoord(cell.x() + sideX, cell.y(), cell.z() + sideZ), "стенка", cell);
            if (forward != null) {
                return forward;
            }
            String backward = DescentDanger.fluidPassage(
                    view, new BlockCoord(cell.x() - sideX, cell.y(), cell.z() - sideZ), "стенка", cell);
            if (backward != null) {
                return backward;
            }
            String passage = DescentDanger.fluidPassage(view, cell, "проход", cell);
            if (passage != null) {
                return passage;
            }
        }
        return null;
    }
}
