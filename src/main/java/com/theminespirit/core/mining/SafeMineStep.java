package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

/**
 * Один шаг безопасной шахты: ступень лесенки или отрезок штольни.
 *
 * <p>Проход — колонка блоков, в которой стоит дух: у штольни два блока (ноги и голова),
 * у лесенки три (лишний блок над головой даёт духу пройти на ступень ниже, не упёршись в потолок).
 * Пол под ногами и боковые стенки могут таить жидкость: такие клетки перечислены в {@link #fill()}
 * и дух закладывает их блоком из инвентаря перед копанием. Сыпучий блок над головой
 * ({@link #fallingAbove()}) сначала падает, а потом его выкапывают; пока это не сделано, дух в проход
 * не заходит.
 */
public record SafeMineStep(
        int index,
        int floor,
        BlockCoord stand,
        List<BlockCoord> passage,
        List<BlockCoord> fill,
        boolean fallingAbove,
        boolean torch) {

    public static final int MIN_PASSAGE_HEIGHT = 2;

    public SafeMineStep {
        if (index < 0) {
            throw new IllegalArgumentException("Номер шага " + index + " отрицательный: шаги считаются от нуля");
        }
        Objects.requireNonNull(stand, "Клетка ступени не может быть null: дух встаёт именно на неё");
        Objects.requireNonNull(passage, "Проход шага не может быть null: без клеток непонятно, что копать");
        Objects.requireNonNull(fill, "Список заделок не может быть null: пустой набор задают пустым списком");
        if (passage.size() < MIN_PASSAGE_HEIGHT) {
            throw new IllegalArgumentException("Проход шага " + index + " состоит из " + passage.size()
                    + " клеток, а дух в полный рост не влезает меньше чем в " + MIN_PASSAGE_HEIGHT
                    + ": шахта копает минимум две клетки в высоту");
        }
        if (floor != stand.y() - 1) {
            throw new IllegalArgumentException("Этаж " + floor + " шага " + index + " не пол под ступенью " + stand
                    + ": пол — ровно блок под ногами духа");
        }
        for (int offset = 0; offset < passage.size(); offset++) {
            BlockCoord cell = passage.get(offset);
            if (cell.y() != floor + 1 + offset) {
                throw new IllegalArgumentException("Клетка " + cell + " прохода шага " + index
                        + " не стоит на высоте floor+" + (offset + 1) + ": проход идёт ровно вверх от пола");
            }
        }
        if (!passage.get(0).equals(stand)) {
            throw new IllegalArgumentException("Нижняя клетка прохода " + passage.get(0) + " шага " + index
                    + " не ступень " + stand + ": дух встаёт на нижнюю клетку своего прохода");
        }
        if (fill.contains(stand)) {
            throw new IllegalArgumentException("Клетка ступени " + stand + " шага " + index
                    + " попала в заделки: своим блоком дух запирает собственный путь");
        }
        passage = List.copyOf(passage);
        fill = List.copyOf(fill);
    }
}
