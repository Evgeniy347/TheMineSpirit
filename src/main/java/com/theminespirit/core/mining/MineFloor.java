package com.theminespirit.core.mining;

import java.util.Objects;
import java.util.Set;

/**
 * Пол хода — клетка {@code @floor}: стоит дешёвый твёрдый блок — не трогаем; воздух, жидкость,
 * трава — ставим булыжник; руда — по правилу руды ({@link MineOres}); гравий и песок без опоры под ними — заменить,
 * иначе обвалятся из-под ног.
 */
public final class MineFloor {
    public enum Action {
        KEEP,
        PLACE,
        REPLACE,
        ORE
    }

    /** Что видно в клетке пола: id блока и свойства, которые решают судьбу клетки. */
    public record Seen(String blockId, boolean air, boolean fluid, boolean replaceable, boolean solidBelow) {
        public Seen {
            Objects.requireNonNull(blockId, "Id блока не может быть null: по нему решается, годится ли пол");
        }
    }

    private static final Set<String> FALLING =
            Set.of("gravel", "sand", "red_sand", "suspicious_sand", "suspicious_gravel");

    private MineFloor() {}

    public static Action decide(Seen seen) {
        Objects.requireNonNull(seen, "Клетка пола не может быть null");
        if (seen.air() || seen.fluid() || seen.replaceable()) {
            return Action.PLACE;
        }
        if (MineOres.kindOf(seen.blockId()) != MineOres.Kind.NONE) {
            return Action.ORE;
        }
        if (FALLING.contains(path(seen.blockId())) && !seen.solidBelow()) {
            return Action.REPLACE;
        }
        return Action.KEEP;
    }

    private static String path(String blockId) {
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }
}
