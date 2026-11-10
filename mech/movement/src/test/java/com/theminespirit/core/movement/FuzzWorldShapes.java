package com.theminespirit.core.movement;

final class FuzzWorldShapes {
    private FuzzWorldShapes() {}

    static void pit(FuzzWorld self, int x, int z, int depth) {
        for (int y = 0; y >= 1 - depth; y--) {
            self.set(x, y, z, FuzzWorld.Cell.AIR);
        }
    }

    static void room(FuzzWorld self, int height, boolean roof, boolean withDoor) {
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = 1; y <= height; y++) {
                    boolean wall = x == 4 || x == 6 || z == 4 || z == 6;
                    self.set(x, y, z, wall ? FuzzWorld.Cell.DIRT : FuzzWorld.Cell.AIR);
                }
            }
        }
        if (roof) {
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    self.set(x, height + 1, z, FuzzWorld.Cell.DIRT);
                }
            }
        }
        if (withDoor) {
            self.set(5, 1, 4, FuzzWorld.Cell.DOOR);
            self.set(5, 2, 4, FuzzWorld.Cell.DOOR);
        }
    }

    static void lintelRoom(FuzzWorld self, int variant) {
        room(self, 3, true, false);
        self.set(5, 1, 4, FuzzWorld.Cell.AIR);
        self.set(5, 2, 4, FuzzWorld.Cell.DIRT);
        self.set(5, 3, 4, FuzzWorld.Cell.DIRT);
        if (variant % 2 == 0) {
            self.set(4, 1, 5, FuzzWorld.Cell.TRAPDOOR);
        } else {
            self.set(4, 1, 5, FuzzWorld.Cell.BED);
        }
    }

    static void slit(FuzzWorld self, int variant) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 1; z <= 4; z++) {
                for (int y = 1; y <= 2; y++) {
                    boolean wall = x == 2 || x == 8 || z == 1 || z == 4;
                    self.set(x, y, z, wall ? FuzzWorld.Cell.STONE : FuzzWorld.Cell.AIR);
                }
            }
        }
        for (int x = 1; x <= 9; x++) {
            for (int z = 0; z <= 5; z++) {
                self.set(x, 3, z, FuzzWorld.Cell.DIRT);
            }
        }
        self.set(4, 1, 4, FuzzWorld.Cell.AIR);
        self.set(5, 1, 4, FuzzWorld.Cell.AIR);
        if (variant % 2 == 0) {
            self.set(4, 2, 4, FuzzWorld.Cell.STONE);
            self.set(5, 2, 4, FuzzWorld.Cell.STONE);
        }
    }

    static void caveMouth(FuzzWorld self, int variant) {
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                for (int y = -1; y <= 1; y++) {
                    self.set(x, y, z, FuzzWorld.Cell.AIR);
                }
            }
        }
        for (int x = 3; x <= 6; x++) {
            for (int z = 3; z <= 7; z++) {
                self.set(x, 2, z, FuzzWorld.Cell.DIRT);
            }
        }
        self.set(7, 1, 2, FuzzWorld.Cell.AIR);
        self.set(7, 1, 3, FuzzWorld.Cell.AIR);
        self.set(7, 1, 4, FuzzWorld.Cell.AIR);
        self.set(7, 1, 5, FuzzWorld.Cell.AIR);
        self.set(7, 1, 6, FuzzWorld.Cell.AIR);
        self.set(7, 1, 7, FuzzWorld.Cell.AIR);
        self.set(7, 2, 2, FuzzWorld.Cell.AIR);
        self.set(7, 2, 3, FuzzWorld.Cell.AIR);
        self.set(7, 2, 4, FuzzWorld.Cell.AIR);
        self.set(7, 2, 5, FuzzWorld.Cell.AIR);
        self.set(7, 2, 6, FuzzWorld.Cell.AIR);
        self.set(7, 2, 7, FuzzWorld.Cell.AIR);
        if (variant % 2 == 0) {
            self.set(4, -1, 5, FuzzWorld.Cell.WATER);
        }
    }

    static void platform(FuzzWorld self, int x0, int z0, int x1, int z1, int height) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 1; y <= height; y++) {
                    self.set(x, y, z, FuzzWorld.Cell.DIRT);
                }
            }
        }
    }

    static void stairs(FuzzWorld self, int steps) {
        for (int step = 0; step < steps; step++) {
            int x = 3 + step;
            int floor = 1 - steps + step;
            for (int y = FuzzWorld.Y_MIN; y <= floor; y++) {
                self.set(x, y, 5, FuzzWorld.Cell.DIRT);
            }
            for (int y = floor + 1; y <= FuzzWorld.Y_MAX; y++) {
                self.set(x, y, 5, FuzzWorld.Cell.AIR);
            }
        }
        for (int x = 1; x < 3; x++) {
            for (int z = 4; z <= 6; z++) {
                self.set(x, 1, z, FuzzWorld.Cell.AIR);
                self.set(x, 2, z, FuzzWorld.Cell.AIR);
            }
        }
    }

    static void waterPit(FuzzWorld self, int x, int z, int depth) {
        pit(self, x, z, depth);
        for (int y = 1 - depth; y <= 1; y++) {
            self.set(x, y, z, FuzzWorld.Cell.WATER);
        }
        self.set(x, -depth, z, FuzzWorld.Cell.DIRT);
    }

    static void ledge(FuzzWorld self, int variant) {
        for (int x = 3; x <= 7; x++) {
            for (int z = 5; z <= 5; z++) {
                self.set(x, 1, z, FuzzWorld.Cell.DIRT);
                self.set(x, 2, z, FuzzWorld.Cell.DIRT);
            }
        }
        if (variant % 2 == 0) {
            self.set(5, 5, 5, FuzzWorld.Cell.DIRT);
            self.set(4, 5, 5, FuzzWorld.Cell.DIRT);
            self.set(6, 5, 5, FuzzWorld.Cell.DIRT);
        }
    }

    static void cage(FuzzWorld self) {
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean shell = x == 4 || x == 6 || z == 4 || z == 6 || y == 1 || y == 4;
                    self.set(x, y, z, shell ? FuzzWorld.Cell.CHEST : FuzzWorld.Cell.AIR);
                }
            }
        }
    }

    static String name(int type) {
        return switch (type) {
            case 0 -> "pit";
            case 1 -> "pit-under-chest";
            case 2 -> "cell-with-roof";
            case 3 -> "cell-without-roof";
            case 4 -> "lintel";
            case 5 -> "slit-height-one";
            case 6 -> "cave-mouth";
            case 7 -> "pillar-2x2";
            case 8 -> "stairs";
            case 9 -> "water";
            case 10 -> "ledge";
            case 11 -> "narrow-support";
            default -> "mixed";
        };
    }
}
