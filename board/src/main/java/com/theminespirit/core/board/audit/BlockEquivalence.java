package com.theminespirit.core.board.audit;

import com.theminespirit.core.building.BlockMatch;
import com.theminespirit.core.building.EarthCover;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicGround;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BlockEquivalence {
    public enum Verdict {
        DONE,
        STATE,
        DIFFER
    }

    public record Rules(Set<String> voidish, Set<String> replaceable, Set<String> soft, Set<String> ground) {
        public static final Rules DEFAULT = new Rules(
                Set.of("air", "cave_air", "void_air"),
                Set.of(
                        "water",
                        "lava",
                        "snow",
                        "short_grass",
                        "grass",
                        "fern",
                        "large_fern",
                        "tall_grass",
                        "dead_bush",
                        "seagrass",
                        "kelp_plant"),
                Set.of(
                        "poppy",
                        "dandelion",
                        "short_grass",
                        "tall_grass",
                        "carrots",
                        "potatoes",
                        "beetroots",
                        "oak_sapling",
                        "vine",
                        "snow",
                        "white_carpet"),
                Set.of("dirt", "grass_block", "dirt_path", "path", "coarse_dirt", "podzol", "mycelium", "farmland"));

        public Rules {
            voidish = Set.copyOf(
                    Objects.requireNonNull(voidish, "Пустота не может быть null: пустой список — пустое множество"));
            replaceable = Set.copyOf(
                    Objects.requireNonNull(replaceable, "Замещаемое не может быть null: иначе жидкость станет блоком"));
            soft = Set.copyOf(
                    Objects.requireNonNull(soft, "Мягкие блоки не могут быть null: иначе трава станет дефектом"));
            ground = Set.copyOf(
                    Objects.requireNonNull(ground, "Класс земли не может быть null: тропа перестанет грунтом"));
        }
    }

    private BlockEquivalence() {}

    public static Verdict check(Wanted wanted, String actual) {
        return check(wanted, actual, Rules.DEFAULT);
    }

    public static Verdict check(Wanted wanted, String actual, Rules rules) {
        Objects.requireNonNull(wanted, "Ожидание не может быть null: без него сверять не с чем");
        Objects.requireNonNull(actual, "Стоящий блок не может быть null: пустоту передают как air");
        String standing = bare(SchematicBlocks.path(actual));
        return switch (wanted.kind()) {
            case AIR ->
                rules.voidish().contains(standing) || rules.replaceable().contains(standing)
                        ? Verdict.DONE
                        : Verdict.DIFFER;
            case GROUND -> rules.ground().contains(standing) ? Verdict.DONE : Verdict.DIFFER;
            case BLOCK -> block(wanted, actual, standing, rules);
        };
    }

    public static boolean matches(Wanted wanted, String actual, Rules rules) {
        return check(wanted, actual, rules) == Verdict.DONE;
    }

    public static String cover(String id, Rules rules) {
        String path = bare(SchematicBlocks.path(id));
        return rules.ground().contains(path) ? EarthCover.DIRT : path;
    }

    public static boolean stateOk(Wanted wanted, String actual) {
        return stateOk(wanted.state(), actual);
    }

    private static Verdict block(Wanted wanted, String actual, String standing, Rules rules) {
        String wantedPath = bare(SchematicBlocks.path(wanted.id()));
        if (SchematicBlocks.isFloorClass(wanted.id()) && wanted.state().isBlank()) {
            return SchematicBlocks.isFloorClass(standing) ? Verdict.DONE : Verdict.DIFFER;
        }
        if (wanted.state().isBlank() && SchematicGround.walkwayPlaced(wanted.id(), actual)) {
            return Verdict.DONE;
        }
        if (BlockMatch.matches(actual, wanted.id())) {
            return stateOk(wanted.state(), actual) ? Verdict.DONE : Verdict.STATE;
        }
        if (rules.soft().contains(wantedPath)) {
            return Verdict.DONE;
        }
        return Verdict.DIFFER;
    }

    private static boolean stateOk(String state, String actual) {
        if (state.isBlank()) {
            return true;
        }
        Map<String, String> standing = properties(actual);
        for (Map.Entry<String, String> wanted : properties(state).entrySet()) {
            if (!wanted.getValue().equals(standing.get(wanted.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> properties(String withState) {
        int open = withState.indexOf('[');
        int at = open < 0 ? 0 : open + 1;
        int close = withState.indexOf(']', at);
        int stop = close < 0 ? withState.length() : close;
        Map<String, String> out = new LinkedHashMap<>();
        while (at < stop) {
            int comma = withState.indexOf(',', at);
            int edge = comma < 0 || comma > stop ? stop : comma;
            int equals = withState.indexOf('=', at);
            if (equals > at && equals < edge) {
                out.put(
                        withState.substring(at, equals).trim(),
                        withState.substring(equals + 1, edge).trim());
            }
            at = edge + 1;
        }
        return Map.copyOf(out);
    }

    private static String bare(String path) {
        int bracket = path.indexOf('[');
        return bracket < 0 ? path : path.substring(0, bracket);
    }
}
