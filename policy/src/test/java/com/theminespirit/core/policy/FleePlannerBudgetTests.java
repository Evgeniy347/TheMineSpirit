package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FleePlannerBudgetTests {
    private static final BlockCoord CORNER = new BlockCoord(0, 0, 0);

    private static AsciiWorld world(String... layers) {
        return AsciiWorld.of(CORNER, Cell.SOLID, layers);
    }

    @Test
    void plan_NodeLimit_CapsVisitedCells() {
        String floor = String.join("/", Collections.nCopies(15, "###############"));
        String open = String.join("/", Collections.nCopies(15, "..............."));
        String feet = String.join("/", Collections.nCopies(7, "...............")) + "/1......@......./"
                + String.join("/", Collections.nCopies(7, "..............."));
        AsciiWorld world = world(floor, feet, open);

        FleePlanner.Plan plan = FleePlanner.plan(
                world.marker('@'), List.of(new FleePlanner.Threat(world.marker('1'), 100)), world, 5, 8);

        assertTrue(
                plan.visited() <= 5,
                "Предел узлов 5: поиск не выходит за него, чтобы тик не зависел от размера открытого поля, иначе "
                        + "на большом поле тик духа проваливается. Посещено: "
                        + plan.visited() + ". При смене лимита проверь обрезку очереди на открытом поле");
    }

    @Test
    void plan_ReadBudget_CapsReadsOfTheView() {
        List<String> floorRows = new ArrayList<>();
        for (int z = 0; z < 15; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 15; x++) {
                row.append(x % 3 == 1 && z % 3 == 1 ? 'H' : '#');
            }
            floorRows.add(row.toString());
        }
        String open = String.join("/", Collections.nCopies(15, "..............."));
        String feet = String.join("/", Collections.nCopies(7, "...............")) + "/1......@......./"
                + String.join("/", Collections.nCopies(7, "..............."));
        AsciiWorld world = world(String.join("/", floorRows), feet, open);

        FleePlanner.plan(world.marker('@'), List.of(new FleePlanner.Threat(world.marker('1'), 100)), world, 2, 8);

        assertTrue(
                world.reads() <= 12,
                "Бюджет чтений 2 узла × 6 = 12: поле с магмой отклоняет каждую стоянку, и без бюджета проверки стоянок читали бы больше, "
                        + "иначе тик зависит от рельефа. Прочитано: "
                        + world.reads() + ". При смене проверки стоянок проверь, что чтений на узел не больше шести");
    }

    @Test
    void threat_NonPositiveDistance_ThrowsWithValue() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new FleePlanner.Threat(CORNER, 0),
                "нулевое расстояние держания не имеет смысла, иначе любая клетка считается безопасной и дух стоит "
                        + "под ударом. При смене конструктора Threat проверь 0 и отрицательное значение");

        assertTrue(
                error.getMessage().contains("0.0"),
                "ошибка называет принятое значение, иначе по логу не понять, что передали, и вызов не исправить. Фактическое: "
                        + error.getMessage()
                        + ". При смене текста ошибки проверь, что в ней остаются параметр и число 0.0");
    }

    @Test
    void plan_ZeroNodes_ThrowsWithValue() {
        AsciiWorld world = world("###", "#@1", "###");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FleePlanner.plan(world.marker('@'), List.of(), world, 0, 8),
                "maxNodes = 0 — ошибка вызова, иначе поиск не делает ни шага и молча возвращает пустой план, "
                        + "а дух думает, что путь построен. При смене проверки параметров проверь ноль");

        assertTrue(
                error.getMessage().contains("maxNodes 0"),
                "ошибка называет параметр и значение, иначе вызов не исправить и дефект повторится в другом месте. Фактическое: "
                        + error.getMessage()
                        + ". При смене текста ошибки проверь, что в ней остаются «maxNodes» и ноль");
    }
}
