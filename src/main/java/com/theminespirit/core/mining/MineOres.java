package com.theminespirit.core.mining;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class MineOres {
    public enum Kind {
        NONE,
        ORDINARY,
        LUCK
    }

    public static final Set<OreKind> LUCK_KINDS = Set.of(OreKind.DIAMOND, OreKind.EMERALD, OreKind.LAPIS);

    public record Pick(int slot, boolean correctForDrops, int fortune, boolean silkTouch) {
        public Pick {
            if (fortune < 0) {
                throw new IllegalArgumentException("Уровень Удачи " + fortune + " отрицательный: зачарование "
                        + "читается из предмета, а не задаётся вручную");
            }
        }
    }

    public sealed interface Decision permits Mine, Leave {}

    public record Mine(OptionalInt slot, String how) implements Decision {
        public Mine {
            Objects.requireNonNull(slot, "Слот не может быть null: любой инструмент — OptionalInt.empty()");
            Objects.requireNonNull(how, "Способ добычи не может быть null: он идёт в журнал стройки");
        }
    }

    public record Leave(String reason) implements Decision {
        public Leave {
            Objects.requireNonNull(reason, "Причина не может быть null: без неё игрок не поймёт, почему руда в стене");
        }
    }

    private MineOres() {}

    public static Kind kindOf(String blockId) {
        Optional<OreKind> ore = OreKind.ofBlock(blockId);
        if (ore.isEmpty()) {
            return Kind.NONE;
        }
        return LUCK_KINDS.contains(ore.get()) ? Kind.LUCK : Kind.ORDINARY;
    }

    public static Decision decide(String blockId, List<Pick> picks) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: по нему решается, руда ли это");
        Objects.requireNonNull(picks, "Кирки не могут быть null: без кирок — пустой список");
        return switch (kindOf(blockId)) {
            case NONE -> new Mine(OptionalInt.empty(), "порода");
            case ORDINARY -> ordinary(blockId, picks);
            case LUCK -> luck(blockId, picks);
        };
    }

    private static Decision ordinary(String blockId, List<Pick> picks) {
        if (picks.stream().anyMatch(Pick::correctForDrops)) {
            return new Mine(OptionalInt.empty(), "подходящей киркой");
        }
        return new Leave("руда " + blockId + " требует кирки выше ступенью: простая ломка потеряет руду, "
                + "оставлена до подходящей кирки");
    }

    private static Decision luck(String blockId, List<Pick> picks) {
        Pick fortune = null;
        Pick silk = null;
        for (Pick pick : picks) {
            if (!pick.correctForDrops()) {
                continue;
            }
            if (pick.fortune() > 0 && (fortune == null || pick.fortune() > fortune.fortune())) {
                fortune = pick;
            }
            if (pick.silkTouch() && silk == null) {
                silk = pick;
            }
        }
        if (fortune != null) {
            return new Mine(OptionalInt.of(fortune.slot()), "киркой с Удачей " + fortune.fortune());
        }
        if (silk != null) {
            return new Mine(OptionalInt.of(silk.slot()), "Шёлковым касанием — блок целиком на склад");
        }
        return new Leave("руда на удачу " + blockId + ": нет кирки с Удачей или Шёлковым касанием, "
                + "оставлена в стене и записана в память поселения");
    }
}
