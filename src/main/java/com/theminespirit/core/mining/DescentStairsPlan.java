package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record DescentStairsPlan(
        BlockCoord entry,
        DescentDirection direction,
        int widenWidth,
        int stairWidth,
        List<DescentStairStep> steps,
        DescentStop stop,
        String dangerReason) {

    public DescentStairsPlan {
        Objects.requireNonNull(entry, "Точка входа не может быть null: без неё неизвестно, где начало спуска");
        Objects.requireNonNull(direction, "Направление спуска не может быть null: от него зависят боковые колонки");
        Objects.requireNonNull(steps, "Шаги ступенек не могут быть null: пустой список — ступенек нет");
        Objects.requireNonNull(stop, "Причина остановки не может быть null: TARGET_Y или DANGER");
        Objects.requireNonNull(dangerReason, "Описание опасности не может быть null: пустая строка — опасности нет");
        if (widenWidth < 1 || widenWidth % 2 == 0) {
            throw new IllegalArgumentException("Ширина расширения " + widenWidth
                    + " обязана быть нечётной и положительной: проход симметричен центральной колонке спуска");
        }
        if (stairWidth < 1 || stairWidth > widenWidth - 1) {
            throw new IllegalArgumentException("Ширина ступенек " + stairWidth + " обязана быть от 1 до "
                    + (widenWidth - 1) + ": третью полосу оставляют под будущие рельсы");
        }
        steps = List.copyOf(steps);
        if (stop == DescentStop.DANGER && dangerReason.isBlank()) {
            throw new IllegalArgumentException("Остановка DANGER без описания опасности: игрок не поймёт, "
                    + "что остановило ступеньки, назовите блок и клетку");
        }
        if (stop == DescentStop.TARGET_Y && !dangerReason.isBlank()) {
            throw new IllegalArgumentException("Остановка TARGET_Y с описанием '" + dangerReason + "': "
                    + "до цели дошли без опасности, лишнее описание запутывает отчёт скрипта");
        }
    }

    public static DescentStairsPlan from(SafetyView view, DescentPlan level1, int widenWidth, int stairWidth) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не проверить безопасность ступенек");
        Objects.requireNonNull(level1, "Геометрия уровня 1 не может быть null: ступеньки идут по существующему спуску");
        if (widenWidth < 1 || widenWidth % 2 == 0) {
            throw new IllegalArgumentException("Ширина расширения " + widenWidth
                    + " обязана быть нечётной и положительной: проход симметричен центральной колонке спуска");
        }
        if (stairWidth < 1 || stairWidth > widenWidth - 1) {
            throw new IllegalArgumentException("Ширина ступенек " + stairWidth + " обязана быть от 1 до "
                    + (widenWidth - 1) + ": третью полосу оставляют под будущие рельсы");
        }
        DescentDirection direction = level1.direction();
        int sideX = -direction.dz;
        int sideZ = direction.dx;
        int half = widenWidth / 2;
        int[] lanes = new int[widenWidth];
        for (int lane = -half; lane <= half; lane++) {
            lanes[lane + half] = lane;
        }
        int railCount = widenWidth - stairWidth;
        List<Integer> railLanes = new ArrayList<>();
        for (int lane : lanes) {
            railLanes.add(lane);
        }
        railLanes.sort(Comparator.comparingInt((Integer lane) -> Math.abs(lane)).thenComparingInt(lane -> lane));
        railLanes = new ArrayList<>(railLanes.subList(0, railCount));
        List<Integer> stairLanes = new ArrayList<>();
        for (int lane : lanes) {
            if (!railLanes.contains(lane)) {
                stairLanes.add(lane);
            }
        }
        List<DescentStairStep> steps = new ArrayList<>();
        String dangerReason = null;
        for (DescentStep base : level1.steps()) {
            List<BlockCoord> stairCells = new ArrayList<>(stairWidth);
            for (int lane : stairLanes) {
                stairCells.add(new BlockCoord(
                        base.stand().x() + sideX * lane,
                        base.floor() + 1,
                        base.stand().z() + sideZ * lane));
            }
            List<BlockCoord> railCells = new ArrayList<>(railCount);
            for (int lane : railLanes) {
                railCells.add(new BlockCoord(
                        base.stand().x() + sideX * lane,
                        base.floor() + 1,
                        base.stand().z() + sideZ * lane));
            }
            String danger = stairsDangerAt(view, base, stairCells, railCells);
            if (danger != null) {
                dangerReason = danger;
                break;
            }
            steps.add(new DescentStairStep(base.index(), base.floor(), stairCells, railCells));
        }
        if (dangerReason == null && level1.stop() == DescentStop.DANGER) {
            dangerReason = level1.dangerReason();
        }
        DescentStop stop = dangerReason == null ? DescentStop.TARGET_Y : DescentStop.DANGER;
        return new DescentStairsPlan(
                level1.entry(),
                direction,
                widenWidth,
                stairWidth,
                steps,
                stop,
                dangerReason == null ? "" : dangerReason);
    }

    private static String stairsDangerAt(
            SafetyView view, DescentStep base, List<BlockCoord> stairCells, List<BlockCoord> railCells) {
        for (BlockCoord stair : stairCells) {
            BlockCoord floor = new BlockCoord(stair.x(), base.floor(), stair.z());
            String reason = DescentDanger.solidFloor(view, floor, "пол", base.index());
            if (reason != null) {
                return reason;
            }
            reason = DescentDanger.fluidCell(view, stair, "клетка ступеньки");
            if (reason != null) {
                return reason;
            }
        }
        for (BlockCoord rail : railCells) {
            String reason = DescentDanger.fluidCell(view, rail, "клетка рельсового прохода");
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }
}
