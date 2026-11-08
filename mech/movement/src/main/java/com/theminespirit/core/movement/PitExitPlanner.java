package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class PitExitPlanner {
    public enum Kind {
        AIR,
        SOFT,
        HARD,
        FALLING,
        WATER,
        LAVA,
        BEDROCK
    }

    @FunctionalInterface
    public interface World {
        Kind at(BlockCoord cell);
    }

    public record Supplies(int blocks, boolean pickaxe) {
        public Supplies {
            if (blocks < 0) {
                throw new IllegalArgumentException("Блоков для выхода " + blocks + ": отрицательный запас не бывает");
            }
        }
    }

    public enum Method {
        WALK,
        PILLAR,
        STAIRS,
        IMPOSSIBLE
    }

    public record Plan(
            Method method, BlockCoord exit, int rise, List<BlockCoord> dig, List<BlockCoord> place, String why) {
        public Plan {
            Objects.requireNonNull(method, "Способ выхода не может быть null: без него неизвестно, что делать");
            Objects.requireNonNull(exit, "Точка выхода не может быть null: духу некуда идти после спасения");
            Objects.requireNonNull(why, "Причина выхода не может быть null: журнал без неё нем");
            if (rise < 0) {
                throw new IllegalArgumentException("Подъём " + rise + " отрицателен: из ямы не спускаются");
            }
            dig = List.copyOf(Objects.requireNonNull(dig, "Список блоков на слом не может быть null"));
            place = List.copyOf(Objects.requireNonNull(place, "Список блоков на постановку не может быть null"));
        }

        public boolean possible() {
            return method != Method.IMPOSSIBLE;
        }

        public List<BlockCoord> restore() {
            return dig;
        }
    }

    public static final int RADIUS = 5;
    public static final int SCAN_RADIUS = 6;
    public static final int MAX_RISE = 40;

    private PitExitPlanner() {}

    public static Plan plan(BlockCoord feet, World world, Supplies supplies) {
        return plan(feet, world, supplies, Set.of());
    }

    public static Plan plan(BlockCoord feet, World world, Supplies supplies, Set<BlockCoord> occupied) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: от них строится выход");
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно ни ямы, ни стены");
        Objects.requireNonNull(supplies, "Запас духа не может быть null: без него не выбрать способ");
        Objects.requireNonNull(occupied, "Занятые клетки не могут быть null: пусто — пустой набор");
        Set<BlockCoord> protectedCells = protect(occupied);

        int surface = PitStairPlanner.surface(feet, world);
        Optional<BlockCoord> swim = swim(feet, world, protectedCells, surface);
        if (swim.isPresent()) {
            return new Plan(Method.WALK, swim.orElseThrow(), 0, List.of(), List.of(), "всплывает по воде к краю");
        }
        Optional<BlockCoord> rim = rim(feet, world);
        if (rim.isEmpty()) {
            Optional<BlockCoord> walk = walkable(feet, world, protectedCells);
            if (walk.isPresent() && walk.orElseThrow().y() >= surface - 1) {
                return new Plan(Method.WALK, walk.orElseThrow(), 0, List.of(), List.of(), "выход на ровный пол");
            }
        } else {
            BlockCoord exit = rim.orElseThrow();
            int rise = exit.y() - feet.y() - 1;
            if (rise <= 0 && exit.y() >= surface - 1) {
                return new Plan(Method.WALK, exit, 0, List.of(), List.of(), "край на шаг выше: выходит пешком");
            }
            if (rise > 0 && supplies.blocks() >= rise && exit.y() >= surface - 1) {
                if (columnClear(feet, rise, world)) {
                    return new Plan(
                            Method.PILLAR,
                            exit,
                            rise,
                            List.of(),
                            List.of(),
                            "край выше на " + rise + ": ставит столб из своих блоков");
                }
            }
        }

        Plan stairs = PitStairPlanner.stairs(feet, world, supplies, protectedCells, surface);
        if (stairs != null) {
            return stairs;
        }
        return new Plan(
                Method.IMPOSSIBLE,
                feet,
                0,
                List.of(),
                List.of(),
                "нет прохода: стены не режутся, лава или бедрок вокруг");
    }

    private static boolean columnClear(BlockCoord feet, int rise, World world) {
        for (int dy = 0; dy <= rise; dy++) {
            if (!passable(world.at(new BlockCoord(feet.x(), feet.y() + dy, feet.z())))) {
                return false;
            }
        }
        return true;
    }

    private static Set<BlockCoord> protect(Set<BlockCoord> occupied) {
        Set<BlockCoord> cells = new HashSet<>();
        for (BlockCoord cell : occupied) {
            cells.add(cell);
            cells.add(below(cell));
            cells.add(above(cell));
        }
        return cells;
    }

    private static Optional<BlockCoord> walkable(BlockCoord feet, World world, Set<BlockCoord> protectedCells) {
        for (int[] side : sides()) {
            BlockCoord same = new BlockCoord(feet.x() + side[0], feet.y(), feet.z() + side[1]);
            if (protectedCells.contains(same)) {
                continue;
            }
            if (standable(same, world)) {
                return Optional.of(same);
            }
            BlockCoord up = new BlockCoord(same.x(), same.y() + 1, same.z());
            if (passable(world.at(above(feet))) && standable(up, world)) {
                return Optional.of(up);
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockCoord> swim(
            BlockCoord feet, World world, Set<BlockCoord> protectedCells, int surface) {
        if (world.at(feet) != Kind.WATER) {
            return Optional.empty();
        }
        for (int dy = 1; dy <= MAX_RISE; dy++) {
            BlockCoord column = new BlockCoord(feet.x(), feet.y() + dy, feet.z());
            Kind here = world.at(column);
            if (here == Kind.HARD || here == Kind.SOFT || here == Kind.FALLING || here == Kind.BEDROCK) {
                return Optional.empty();
            }
            for (int[] side : sides()) {
                BlockCoord edge = new BlockCoord(column.x() + side[0], column.y(), column.z() + side[1]);
                if (!protectedCells.contains(edge) && edge.y() >= surface - 1 && standable(edge, world)) {
                    return Optional.of(edge);
                }
            }
        }
        return Optional.empty();
    }

    public static OptionalInt groundY(int x, int z, int from, World world) {
        Objects.requireNonNull(world, "Мир не может быть null: по нему ищется пол колонки");
        return PitStairPlanner.groundY(x, z, from, world);
    }

    private static Optional<BlockCoord> rim(BlockCoord feet, World world) {
        return PitEscape.exit((dx, dz) -> dx == 0 && dz == 0
                        ? feet.y()
                        : PitStairPlanner.groundYOrFeet(feet.x() + dx, feet.z() + dz, feet.y(), world))
                .map(exit -> new BlockCoord(
                        feet.x() + exit.rimDx(),
                        PitStairPlanner.groundYOrFeet(
                                feet.x() + exit.rimDx(), feet.z() + exit.rimDz(), feet.y(), world),
                        feet.z() + exit.rimDz()));
    }

    static boolean standable(BlockCoord cell, World world) {
        return passable(world.at(cell)) && passable(world.at(above(cell))) && supported(world.at(below(cell)));
    }

    static boolean supported(Kind kind) {
        return kind == Kind.SOFT || kind == Kind.HARD || kind == Kind.FALLING || kind == Kind.BEDROCK;
    }

    static boolean passable(Kind kind) {
        return kind == Kind.AIR || kind == Kind.WATER;
    }

    static boolean breakable(Kind kind) {
        return kind == Kind.SOFT || kind == Kind.HARD || kind == Kind.FALLING;
    }

    static boolean touchesLava(BlockCoord cell, World world) {
        for (int[] side : sides()) {
            if (world.at(new BlockCoord(cell.x() + side[0], cell.y(), cell.z() + side[1])) == Kind.LAVA) {
                return true;
            }
        }
        return world.at(above(cell)) == Kind.LAVA || world.at(below(cell)) == Kind.LAVA;
    }

    private static BlockCoord above(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() + 1, cell.z());
    }

    private static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private static int[][] sides() {
        return new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    }
}
