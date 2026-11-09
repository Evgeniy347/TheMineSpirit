package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PitWalkOutTests {
    private static final int START = 20;
    private static final int ROOF = 64;

    @Test
    void walkOut_WalledCellOpenToSky_IsNotAnExit() {
        Grid cell = new Grid(Grid.NONE, ROOF).floorAt(0, 0, START).skyAt(0, 0, START);

        assertFalse(
                PitWalkOut.exists(cell, 0, 0, START),
                "пробитая крыша клетки — не выход: иначе дух возвращает крышу и снова сидит замурованным");
    }

    @Test
    void walkOut_CaveWithNarrowCeilingHole_IsNotAnExit() {
        Grid cave = Grid.cave();
        cave.skyAt(6, 0, START).skyAt(6, 1, START);

        assertFalse(
                PitWalkOut.exists(cave, 0, 0, START),
                "проём в потолке большой пещеры — не выход: дух по-прежнему под землёй, и метка «пути нет» "
                        + "сниматься не должна (debtnav2, п.3)");
    }

    @Test
    void walkOut_CaveWithWideCeilingHole_IsNotAnExit() {
        Grid cave = Grid.cave();
        cave.skyAt(6, 0, START).skyAt(6, 1, START).skyAt(6, 2, START);

        assertFalse(
                PitWalkOut.exists(cave, 0, 0, START),
                "широкий проём в потолке тоже не край пещеры: за ним снова потолок, а не местность на уровне "
                        + "клетки выхода (debtnav2, п.3)");
    }

    @Test
    void walkOut_CaveMouthToOpenSlope_IsAnExit() {
        Grid cave = Grid.cave();
        for (int z = -12; z <= 12; z++) {
            for (int x = 6; x <= 12; x++) {
                cave.skyAt(x, z, START);
            }
        }

        assertTrue(
                PitWalkOut.exists(cave, 0, 0, START),
                "устье пещеры выводит на открытую местность: за краем нет потолка, и метка снимается по делу");
    }

    @Test
    void walkOut_RampFromPitToSurface_IsAnExit() {
        Grid pit = Grid.enclosedPit();

        assertTrue(
                PitWalkOut.exists(pit, 0, 0, START),
                "настоящий перепад высот — подъём по склону к краю ямы — выход: дух выбирается пешком");
    }

    private static final class Grid implements PitWalkOut.World {
        private static final int MIN = -12;
        private static final int SIZE = 25;
        private static final int NONE = Integer.MIN_VALUE;
        private final int[][] floor = new int[SIZE][SIZE];
        private final int[][] sky = new int[SIZE][SIZE];

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
            for (int x = 0; x <= 12; x++) {
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
        public int surfaceY(int x, int z) {
            return inside(x, z) ? sky[x - MIN][z - MIN] : Integer.MAX_VALUE;
        }

        private static boolean inside(int x, int z) {
            return x - MIN >= 0 && x - MIN < SIZE && z - MIN >= 0 && z - MIN < SIZE;
        }
    }
}
