package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * План безопасной шахты: спуск лесенкой до заданного этажа или штольня заданной длины.
 *
 * <p>Проход — один блок шириной и два высотой. Строится по {@link SafetyView}: жидкость сбоку,
 * под ногами или над головой планируется заложить блоком из инвентаря ({@link SafeMineStep#fill()}),
 * провал под ногами останавливает план (дух туда не шагает), сыпучий блок над головой помечается
 * {@link SafeMineStep#fallingAbove()}. Факелы ставятся каждый {@code torchInterval}-й шаг из данных.
 */
public record SafeMinePlan(
        BlockCoord entry,
        SafeMineMode mode,
        DescentDirection direction,
        int torchInterval,
        List<SafeMineStep> steps,
        DescentStop stop,
        String dangerReason) {

    public static final int WORLD_MIN_Y = DescentPlan.WORLD_MIN_Y;
    public static final int WORLD_MAX_Y = DescentPlan.WORLD_MAX_Y;

    public static final int STAIRCASE_PASSAGE_HEIGHT = 3;
    public static final int TUNNEL_PASSAGE_HEIGHT = 2;

    public SafeMinePlan {
        Objects.requireNonNull(entry, "Точка входа не может быть null: без неё неизвестно, откуда копать");
        Objects.requireNonNull(mode, "Форма шахты не может быть null: без неё неизвестно, вниз копать или вбок");
        Objects.requireNonNull(direction, "Направление не может быть null: без него неизвестно, куда копать");
        Objects.requireNonNull(steps, "Шаги не могут быть null: пустой список — шахта уже пройдена");
        Objects.requireNonNull(stop, "Причина остановки не может быть null: TARGET_Y или DANGER");
        Objects.requireNonNull(dangerReason, "Описание опасности не может быть null: пустая строка — опасности нет");
        if (torchInterval < MiningTuning.DESCENT_TORCH_INTERVAL_MIN
                || torchInterval > MiningTuning.DESCENT_TORCH_INTERVAL_MAX) {
            throw new IllegalArgumentException("Интервал факелов " + torchInterval + " вне диапазона ["
                    + MiningTuning.DESCENT_TORCH_INTERVAL_MIN + ", " + MiningTuning.DESCENT_TORCH_INTERVAL_MAX
                    + "]: редкий интервал оставит шахту тёмной, частый разорит запас факелов");
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

    public static SafeMinePlan staircase(
            SafetyView view, BlockCoord entry, DescentDirection direction, int targetY, int torchInterval) {
        requireView(view);
        requireEntry(entry);
        requireDirection(direction);
        if (targetY < WORLD_MIN_Y || targetY > WORLD_MAX_Y) {
            throw new IllegalArgumentException("Целевой этаж " + targetY + " вне мира [" + WORLD_MIN_Y + ", "
                    + WORLD_MAX_Y + "]: под дном мира копать некуда, а выше потолка сверлиться некуда");
        }
        int entryFloor = entry.y() - 1;
        List<SafeMineStep> steps = new ArrayList<>();
        String dangerReason = null;
        int index = 0;
        for (int floor = entryFloor; floor >= targetY; floor--, index++) {
            BlockCoord column =
                    new BlockCoord(entry.x() + index * direction.dx, floor, entry.z() + index * direction.dz);
            Outcome outcome =
                    stepOrDanger(view, index, floor, column, direction, torchInterval, STAIRCASE_PASSAGE_HEIGHT);
            if (outcome instanceof Danger danger) {
                dangerReason = danger.reason();
                break;
            }
            steps.add(((Step) outcome).step());
        }
        return finish(entry, SafeMineMode.STAIRCASE, direction, torchInterval, steps, dangerReason);
    }

    public static SafeMinePlan tunnel(
            SafetyView view, BlockCoord entry, DescentDirection direction, int length, int torchInterval) {
        requireView(view);
        requireEntry(entry);
        requireDirection(direction);
        if (length < 1) {
            throw new IllegalArgumentException("Длина штольни " + length + " меньше единицы: "
                    + "копать нечего, длину задают положительным числом");
        }
        int floor = entry.y() - 1;
        List<SafeMineStep> steps = new ArrayList<>();
        String dangerReason = null;
        for (int index = 0; index < length; index++) {
            BlockCoord column =
                    new BlockCoord(entry.x() + index * direction.dx, floor, entry.z() + index * direction.dz);
            Outcome outcome = stepOrDanger(view, index, floor, column, direction, torchInterval, TUNNEL_PASSAGE_HEIGHT);
            if (outcome instanceof Danger danger) {
                dangerReason = danger.reason();
                break;
            }
            steps.add(((Step) outcome).step());
        }
        return finish(entry, SafeMineMode.TUNNEL, direction, torchInterval, steps, dangerReason);
    }

    private static SafeMinePlan finish(
            BlockCoord entry,
            SafeMineMode mode,
            DescentDirection direction,
            int torchInterval,
            List<SafeMineStep> steps,
            String dangerReason) {
        DescentStop stop = dangerReason == null ? DescentStop.TARGET_Y : DescentStop.DANGER;
        return new SafeMinePlan(
                entry, mode, direction, torchInterval, steps, stop, dangerReason == null ? "" : dangerReason);
    }

    private static Outcome stepOrDanger(
            SafetyView view,
            int index,
            int floor,
            BlockCoord column,
            DescentDirection direction,
            int torchInterval,
            int passageHeight) {
        BlockCoord stand = new BlockCoord(column.x(), floor + 1, column.z());
        List<BlockCoord> passage = new ArrayList<>(passageHeight);
        for (int offset = 0; offset < passageHeight; offset++) {
            passage.add(new BlockCoord(column.x(), floor + 1 + offset, column.z()));
        }
        passage = List.copyOf(passage);
        Set<BlockCoord> fill = new LinkedHashSet<>();

        String floorDanger = floorHazard(view, index, column, floor, fill);
        if (floorDanger != null) {
            return new Danger(floorDanger);
        }
        for (BlockCoord cell : passage) {
            String reason = DescentDanger.fluidCell(view, cell, "проход шага " + index);
            if (reason != null) {
                return new Danger(reason);
            }
        }
        int sideX = -direction.dz;
        int sideZ = direction.dx;
        for (BlockCoord cell : passage) {
            for (int sign = -1; sign <= 1; sign += 2) {
                BlockCoord side = new BlockCoord(cell.x() + sideX * sign, cell.y(), cell.z() + sideZ * sign);
                String reason = DescentDanger.fluidCell(view, side, "стенка шага " + index);
                if (reason != null) {
                    fill.add(side);
                }
            }
        }
        BlockCoord ceiling = new BlockCoord(column.x(), floor + passageHeight + 1, column.z());
        Cell ceilingCell = view.cellAt(ceiling);
        boolean fallingAbove = ceilingCell == Cell.FALLING;
        if (ceilingCell == Cell.WATER || ceilingCell == Cell.LAVA) {
            fill.add(ceiling);
        }
        boolean torch = index % torchInterval == 0;
        return new Step(new SafeMineStep(index, floor, stand, passage, List.copyOf(fill), fallingAbove, torch));
    }

    private static String floorHazard(SafetyView view, int index, BlockCoord column, int floor, Set<BlockCoord> fill) {
        BlockCoord floorCell = new BlockCoord(column.x(), floor, column.z());
        Cell cell = view.cellAt(floorCell);
        if (cell == Cell.SOLID || cell == Cell.FALLING) {
            return null;
        }
        if (cell == Cell.WATER || cell == Cell.LAVA) {
            fill.add(floorCell);
            return null;
        }
        return DescentDanger.solidFloor(view, floorCell, "пол", index);
    }

    private static void requireView(SafetyView view) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не проверить безопасность шагов");
    }

    private static void requireEntry(BlockCoord entry) {
        Objects.requireNonNull(entry, "Точка входа не может быть null: без неё неизвестно, откуда копать");
        if (entry.y() < WORLD_MIN_Y || entry.y() > WORLD_MAX_Y) {
            throw new IllegalArgumentException("Точка входа " + entry + " вне мира по высоте [" + WORLD_MIN_Y + ", "
                    + WORLD_MAX_Y + "]: шахта начинается от ног духа в пределах мира");
        }
    }

    private static void requireDirection(DescentDirection direction) {
        Objects.requireNonNull(direction, "Направление не может быть null: без него неизвестно, куда копать");
    }

    private sealed interface Outcome permits Step, Danger {}

    private record Step(SafeMineStep step) implements Outcome {}

    private record Danger(String reason) implements Outcome {}
}
