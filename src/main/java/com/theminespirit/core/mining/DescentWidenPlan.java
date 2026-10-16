package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record DescentWidenPlan(
        BlockCoord entry,
        DescentDirection direction,
        int targetY,
        int width,
        int height,
        List<DescentWidenStep> steps,
        DescentStop stop,
        String dangerReason) {

    public DescentWidenPlan {
        Objects.requireNonNull(entry, "Точка входа не может быть null: без неё неизвестно, откуда расширять спуск");
        Objects.requireNonNull(direction, "Направление спуска не может быть null: от него зависят боковые колонки");
        Objects.requireNonNull(steps, "Шаги расширения не могут быть null: пустой список — расширять нечего");
        Objects.requireNonNull(stop, "Причина остановки не может быть null: TARGET_Y или DANGER");
        Objects.requireNonNull(dangerReason, "Описание опасности не может быть null: пустая строка — опасности нет");
        if (width < 1 || width % 2 == 0) {
            throw new IllegalArgumentException("Ширина расширения " + width
                    + " обязана быть нечётной и положительной: проход симметричен центральной колонке спуска");
        }
        if (height < 1) {
            throw new IllegalArgumentException("Высота расширения " + height + " меньше единицы: расширять нечем");
        }
        if (targetY < DescentPlan.WORLD_MIN_Y || targetY > DescentPlan.WORLD_MAX_Y) {
            throw new IllegalArgumentException("Целевой этаж " + targetY + " вне мира [" + DescentPlan.WORLD_MIN_Y + ", "
                    + DescentPlan.WORLD_MAX_Y + "]: под дном мира копать некуда, а выше потолка сверлиться некуда");
        }
        steps = List.copyOf(steps);
        if (stop == DescentStop.DANGER && dangerReason.isBlank()) {
            throw new IllegalArgumentException("Остановка DANGER без описания опасности: игрок не поймёт, "
                    + "что остановило расширение, назовите блок и клетку");
        }
        if (stop == DescentStop.TARGET_Y && !dangerReason.isBlank()) {
            throw new IllegalArgumentException("Остановка TARGET_Y с описанием '" + dangerReason + "': "
                    + "до цели дошли без опасности, лишнее описание запутывает отчёт скрипта");
        }
    }

    public static DescentWidenPlan from(SafetyView view, DescentPlan level1, int width, int height) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не проверить безопасность расширения");
        Objects.requireNonNull(level1, "Геометрия уровня 1 не может быть null: расширяют существующий спуск");
        if (width < 1 || width % 2 == 0) {
            throw new IllegalArgumentException("Ширина расширения " + width
                    + " обязана быть нечётной и положительной: проход симметричен центральной колонке спуска");
        }
        if (height < 1) {
            throw new IllegalArgumentException("Высота расширения " + height + " меньше единицы: расширять нечем");
        }
        DescentDirection direction = level1.direction();
        int sideX = -direction.dz;
        int sideZ = direction.dx;
        int half = width / 2;
        List<DescentWidenStep> steps = new ArrayList<>();
        String dangerReason = null;
        for (DescentStep base : level1.steps()) {
            List<BlockCoord> cells = new ArrayList<>(width * height);
            for (int lane = -half; lane <= half; lane++) {
                int columnX = base.stand().x() + sideX * lane;
                int columnZ = base.stand().z() + sideZ * lane;
                for (int above = 1; above <= height; above++) {
                    cells.add(new BlockCoord(columnX, base.floor() + above, columnZ));
                }
            }
            String danger = widenDangerAt(view, base, width, height, sideX, sideZ, cells);
            if (danger != null) {
                dangerReason = danger;
                break;
            }
            steps.add(new DescentWidenStep(base.index(), base.floor(), width, height, base.stand(),
                    List.copyOf(cells)));
        }
        if (dangerReason == null && level1.stop() == DescentStop.DANGER) {
            dangerReason = level1.dangerReason();
        }
        DescentStop stop = dangerReason == null ? DescentStop.TARGET_Y : DescentStop.DANGER;
        return new DescentWidenPlan(level1.entry(), direction, level1.targetY(), width, height, steps, stop,
                dangerReason == null ? "" : dangerReason);
    }

    private static String widenDangerAt(SafetyView view, DescentStep base, int width, int height, int sideX,
            int sideZ, List<BlockCoord> cells) {
        int half = width / 2;
        for (int lane = -half; lane <= half; lane++) {
            BlockCoord floor = new BlockCoord(base.stand().x() + sideX * lane, base.floor(),
                    base.stand().z() + sideZ * lane);
            String reason = DescentDanger.solidFloor(view, floor, "пол", base.index());
            if (reason != null) {
                return reason;
            }
        }
        for (BlockCoord cell : cells) {
            String reason = DescentDanger.fluidCell(view, cell, "клетка расширения");
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }
}
