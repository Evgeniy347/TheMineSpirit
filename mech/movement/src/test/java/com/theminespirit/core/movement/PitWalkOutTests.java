package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PitWalkOutTests {
    private static final int START = 20;
    private static final int ROOF = 64;
    private static final WalkOutRules RULES = new WalkOutRules(32);

    @Test
    void walkOut_StepDownOnOpenHill_IsAnExit() {
        Grid hill = new Grid(START - 1, START - 1);
        hill.floorAt(0, 0, START).skyAt(0, 0, START);

        assertTrue(
                PitWalkOut.exists(hill, RULES, 0, 0, START),
                "спуск на блок с открытого холма — выход: иначе дух на ровной земле считается запертым и "
                        + "простаивает с меткой «не двигается» (spirit-3, spirit-4, -173,71,-489)");
    }

    @Test
    void walkOut_DropThroughSolidGround_IsNotAStep() {
        Grid buried = new Grid(Grid.NONE, ROOF).floorAt(0, 0, START);
        buried.floorAt(1, 0, START - 2).ceilingAt(1, 0, START - 1);

        assertFalse(
                PitWalkOut.exists(buried, RULES, 0, 0, START), "сквозь сплошной блок на нижнюю клетку не спуститься");
    }

    @Test
    void walkOut_StepUpUnderCeilingAboveHead_IsNotAnExit() {
        Grid ramp = Grid.enclosedPit();
        ramp.ceilingAt(0, -1, START + 2).ceilingAt(0, 0, START + 2).ceilingAt(0, 1, START + 2);

        assertFalse(
                PitWalkOut.exists(ramp, RULES, 0, 0, START),
                "из-под низкого потолка на ступень не подпрыгнуть: иначе метка «не двигается» снимается "
                        + "как выход, а дух стоит на месте (spirit-1, -187,65,-514)");
    }

    @Test
    void walkOut_WalledCellOpenToSky_IsNotAnExit() {
        Grid cell = new Grid(Grid.NONE, ROOF).floorAt(0, 0, START).skyAt(0, 0, START);

        assertFalse(
                PitWalkOut.exists(cell, RULES, 0, 0, START),
                "пробитая крыша клетки — не выход: иначе дух возвращает крышу и снова сидит замурованным");
    }

    @Test
    void walkOut_CaveWithNarrowCeilingHole_IsNotAnExit() {
        Grid cave = Grid.cave();
        cave.skyAt(6, 0, START).skyAt(6, 1, START);

        assertFalse(
                PitWalkOut.exists(cave, RULES, 0, 0, START),
                "проём в потолке большой пещеры — не выход: дух по-прежнему под землёй, и метка «пути нет» "
                        + "сниматься не должна (debtnav2, п.3)");
    }

    @Test
    void walkOut_CaveWithWideCeilingHole_IsNotAnExit() {
        Grid cave = Grid.cave();
        cave.skyAt(6, 0, START).skyAt(6, 1, START).skyAt(6, 2, START);

        assertFalse(
                PitWalkOut.exists(cave, RULES, 0, 0, START),
                "широкий проём в потолке тоже не край пещеры: за ним снова потолок, а не местность на уровне "
                        + "клетки выхода (debtnav2, п.3)");
    }

    @Test
    void walkOut_CaveHoleWiderThanThree_IsNotAnExit() {
        Grid cave = Grid.cave();
        for (int z = 0; z <= 3; z++) {
            cave.skyAt(6, z, START);
        }

        assertFalse(
                PitWalkOut.exists(cave, RULES, 0, 0, START),
                "проём в потолке шире трёх клеток не выход: проверка поверхности на одной точке в трёх шагах "
                        + "попадала внутрь проёма и объявляла выход там, где кругом потолок пещеры (debtnav3, п.3)");
    }

    @Test
    void walkOut_CaveHoleSixWide_IsNotAnExit() {
        Grid cave = Grid.cave();
        for (int z = 0; z <= 5; z++) {
            cave.skyAt(6, z, START);
        }

        assertFalse(
                PitWalkOut.exists(cave, RULES, 0, 0, START),
                "проём в потолке в шесть клеток не выход: поверхность вокруг клетки выхода обязана быть местностью, "
                        + "а не дном той же дыры (debtnav3, п.3)");
    }

    @Test
    void walkOut_CaveHoleWiderThanSurfaceSpread_IsNotAnExit() {
        Grid cave = Grid.cave();
        for (int z = 0; z <= 29; z++) {
            cave.skyAt(6, z, START);
        }

        assertFalse(
                PitWalkOut.exists(cave, RULES, 0, 0, START),
                "проём в потолке шире области проверки поверхности не выход: дыра в потолке пещеры любого размера "
                        + "не делает дно пещеры местностью, иначе дух перестаёт искать выход (debtnav3, п.1)");
    }

    @Test
    void walkOut_CaveMouthToOpenSlope_IsAnExit() {
        Grid cave = Grid.cave();
        for (int z = -38; z <= 38; z++) {
            for (int x = 6; x <= 38; x++) {
                cave.skyAt(x, z, START);
            }
        }

        assertTrue(
                PitWalkOut.exists(cave, RULES, 0, 0, START),
                "устье пещеры выводит на открытую местность: за краем нет потолка, и метка снимается по делу");
    }

    @Test
    void walkOut_RampFromPitToSurface_IsAnExit() {
        Grid pit = Grid.enclosedPit();

        assertTrue(
                PitWalkOut.exists(pit, RULES, 0, 0, START),
                "настоящий перепад высот — подъём по склону к краю ямы — выход: дух выбирается пешком");
    }

    @Test
    void walkOut_MinimumSpreadOnRamp_IsAnExit() {
        Grid pit = Grid.enclosedPit();

        assertTrue(
                PitWalkOut.exists(pit, new WalkOutRules(PitWalkOut.EXIT_RADIUS), 0, 0, START),
                "область проверки не меньше радиуса выхода: склон к краю ямы — выход и при минимальной области, "
                        + "а общий буфер обязан вмещать её край без выхода за границы");
    }

    @Test
    void walkOut_WideSkylight_OutcomeFollowsConfiguredSpread() {
        Grid cave = Grid.cave();
        for (int x = 6; x <= 45; x++) {
            for (int z = -20; z <= 20; z++) {
                cave.skyAt(x, z, START);
            }
        }

        assertTrue(
                PitWalkOut.exists(cave, new WalkOutRules(32), 0, 0, START),
                "Предел 32 — данные walkout.json: склеп шире области проверки пока неотличим от открытой "
                        + "местности, и это известный предел, а не случайное число в коде. Если предел убирают "
                        + "семантическим сравнением с рельефом, тест обязан измениться вместе с поведением");
        assertFalse(
                PitWalkOut.exists(cave, new WalkOutRules(64), 0, 0, START),
                "При области 64 поиск видит потолок за проёмом и не выдаёт дно склепа за поверхность: "
                        + "значит число области читается из правил, а не зашито в PitWalkOut");
    }

    @Test
    void walkOut_ManyExitNodesInClosedArena_AskSurfaceOncePerCell() {
        Grid arena = Grid.cave();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                arena.skyAt(x, z, START);
            }
        }
        CountingPitWorld counting = new CountingPitWorld(arena);

        assertFalse(
                PitWalkOut.exists(counting, new WalkOutRules(32), 0, 0, START),
                "арена 41×41 внутри области проверки 32 — не поверхность: за краем снова потолок пещеры");

        assertEquals(
                counting.surfaceCalls(),
                counting.distinctSurfaceCells(),
                "высота поверхности — факт мира, неизменный за один вызов: каждый выходной узел обязан брать её "
                        + "из общей области ямы, а не заново у мира. Повторные запросы на spread=64 (16 641 клетка "
                        + "на узел) греют поиск и грузят сервер");
    }

    @Test
    void parse_SpreadBelowExitRadius_RejectedWithBounds() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> WalkOutRules.parse("{\"surfaceSpread\": 3}", "walkout.json"),
                "Битый датапак обязан падать при загрузке: иначе область проверки уже радиуса выхода "
                        + "молча объявит любую соседнюю клетку поверхностью");

        assertTrue(
                error.getMessage().contains("Область проверки"),
                "Ошибка обязана называть предел области и допустимый диапазон, иначе датапак не починить; "
                        + "получено: " + error.getMessage());
    }

    private static final class Grid implements PitWalkOut.World {
        private static final int MIN = -48;
        private static final int SIZE = 97;
        private static final int NONE = Integer.MIN_VALUE;
        private final int[][] floor = new int[SIZE][SIZE];
        private final int[][] sky = new int[SIZE][SIZE];
        private final Set<List<Integer>> ceiling = new HashSet<>();

        private Grid(int floorY, int skyY) {
            for (int[] row : floor) {
                Arrays.fill(row, floorY);
            }
            for (int[] row : sky) {
                Arrays.fill(row, skyY);
            }
        }

        static Grid cave() {
            return new Grid(START, ROOF);
        }

        static Grid enclosedPit() {
            Grid grid = new Grid(NONE, ROOF);
            for (int x = 0; x <= 38; x++) {
                for (int z = -1; z <= 1; z++) {
                    int ground = START + Math.min(x, 6);
                    grid.floorAt(x, z, ground).skyAt(x, z, ground);
                }
            }
            return grid;
        }

        Grid floorAt(int x, int z, int y) {
            floor[x - MIN][z - MIN] = y;
            return this;
        }

        Grid ceilingAt(int x, int z, int y) {
            ceiling.add(List.of(x, y, z));
            return this;
        }

        Grid skyAt(int x, int z, int y) {
            sky[x - MIN][z - MIN] = y;
            return this;
        }

        @Override
        public OptionalInt groundY(int x, int z, int from) {
            if (!inside(x, z)) {
                return OptionalInt.empty();
            }
            int ground = floor[x - MIN][z - MIN];
            return ground == NONE || ground < from ? OptionalInt.empty() : OptionalInt.of(ground);
        }

        @Override
        public boolean clear(int x, int y, int z) {
            return !ceiling.contains(List.of(x, y, z));
        }

        @Override
        public int surfaceY(int x, int z) {
            return inside(x, z) ? sky[x - MIN][z - MIN] : Integer.MAX_VALUE;
        }

        private static boolean inside(int x, int z) {
            return x - MIN >= 0 && x - MIN < SIZE && z - MIN >= 0 && z - MIN < SIZE;
        }
    }
}
