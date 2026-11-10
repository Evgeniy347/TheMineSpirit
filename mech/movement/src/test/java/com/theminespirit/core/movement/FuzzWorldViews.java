package com.theminespirit.core.movement;

import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import java.util.OptionalInt;

final class FuzzWorldViews {
    private FuzzWorldViews() {}

    static PitExitPlanner.World pitWorld(FuzzWorld self) {
        return cell -> self.cellAt(cell).pit();
    }

    static StuckSituation.Grid grid(FuzzWorld self, BlockCoord feet) {
        return new StuckSituation.Grid() {
            @Override
            public boolean passable(BlockCoord cell) {
                FuzzWorld.Cell value = self.cellAt(cell);
                return value == FuzzWorld.Cell.AIR || value == FuzzWorld.Cell.LADDER;
            }

            @Override
            public boolean swimmable(BlockCoord cell) {
                return self.cellAt(cell) == FuzzWorld.Cell.WATER;
            }

            @Override
            public boolean breakable(BlockCoord cell) {
                FuzzWorld.Cell value = self.cellAt(cell);
                return value == FuzzWorld.Cell.DIRT || value == FuzzWorld.Cell.STONE;
            }

            @Override
            public boolean clearColumn(BlockCoord feet, int rise) {
                for (int dy = 0; dy <= rise + 2; dy++) {
                    FuzzWorld.Cell value = self.cellAt(feet.x(), feet.y() + dy, feet.z());
                    if (value != FuzzWorld.Cell.AIR
                            && value != FuzzWorld.Cell.WATER
                            && value != FuzzWorld.Cell.LADDER) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public int floorY(int x, int z) {
                for (int y = FuzzWorld.Y_MIN + 2; y <= FuzzWorld.Y_MAX; y++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (BodyMotion.standable(self, cell)) {
                        return y;
                    }
                }
                return feet.y();
            }
        };
    }

    static SealedSpace.View sealedView(FuzzWorld self) {
        return new SealedSpace.View() {
            @Override
            public boolean passable(BlockCoord cell) {
                FuzzWorld.Cell value = self.cellAt(cell);
                return value == FuzzWorld.Cell.AIR || value == FuzzWorld.Cell.LADDER;
            }

            @Override
            public boolean swimmable(BlockCoord cell) {
                return self.cellAt(cell) == FuzzWorld.Cell.WATER;
            }

            @Override
            public boolean sky(BlockCoord cell) {
                return self.sky(cell);
            }
        };
    }

    static NavWorld navWorld(FuzzWorld self) {
        return new NavWorld() {
            @Override
            public boolean available(int x, int z) {
                return true;
            }

            @Override
            public boolean passable(int x, int y, int z) {
                return BodyMotion.passable(self, new BlockCoord(x, y, z));
            }

            @Override
            public boolean walkable(int x, int y, int z) {
                return BodyMotion.solid(self, new BlockCoord(x, y, z));
            }

            @Override
            public boolean water(int x, int y, int z) {
                return self.cellAt(x, y, z) == FuzzWorld.Cell.WATER;
            }

            @Override
            public boolean climbable(int x, int y, int z) {
                return self.cellAt(x, y, z) == FuzzWorld.Cell.LADDER;
            }

            @Override
            public double breakTicks(int x, int y, int z) {
                FuzzWorld.Cell value = self.cellAt(x, y, z);
                return value == FuzzWorld.Cell.CHEST
                                || value == FuzzWorld.Cell.DOOR
                                || value == FuzzWorld.Cell.TRAPDOOR
                                || value == FuzzWorld.Cell.BED
                        ? Double.POSITIVE_INFINITY
                        : 10;
            }

            @Override
            public int minY() {
                return FuzzWorld.Y_MIN;
            }

            @Override
            public int maxY() {
                return FuzzWorld.Y_MAX;
            }
        };
    }

    static PitWalkOut.World pitWalkWorld(FuzzWorld self) {
        return new PitWalkOut.World() {
            @Override
            public OptionalInt groundY(int x, int z, int from) {
                if (x < 0 || x >= FuzzWorld.SIZE || z < 0 || z >= FuzzWorld.SIZE) {
                    return OptionalInt.of(1);
                }
                for (int y = Math.max(from, FuzzWorld.Y_MIN + 2); y <= FuzzWorld.Y_MAX; y++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (BodyMotion.standable(self, cell)) {
                        return OptionalInt.of(y);
                    }
                }
                return OptionalInt.empty();
            }

            @Override
            public boolean clear(int x, int y, int z) {
                FuzzWorld.Cell value = self.cellAt(x, y, z);
                return value == FuzzWorld.Cell.AIR || value == FuzzWorld.Cell.WATER;
            }

            @Override
            public int surfaceY(int x, int z) {
                if (x < 0 || x >= FuzzWorld.SIZE || z < 0 || z >= FuzzWorld.SIZE) {
                    return 1;
                }
                for (int y = FuzzWorld.Y_MAX; y >= FuzzWorld.Y_MIN; y--) {
                    if (self.cellAt(x, y, z).blocksSky()) {
                        return y + 1;
                    }
                }
                return FuzzWorld.Y_MIN;
            }
        };
    }
}
