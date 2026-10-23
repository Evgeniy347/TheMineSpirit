package com.theminespirit.core.mining;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Руда в клетке шахты (решение владельца 22.10): обычную — добыть подходящей киркой; руду «на удачу» — только киркой
 * с Удачей, иначе Шёлковым касанием целиком, иначе оставить на месте до такой кирки.
 *
 * <p>Руда «на удачу» — алмазы, изумруды и лазурит: Удача умножает их выход в разы (алмаз и изумруд — до ×4 на
 * Удаче III, лазурит — 4–9 штук до ×4), а сами руды редкие, и ломка простой киркой теряет большую часть добычи
 * навсегда. Уголь, медь, красный камень и кварц обычные и дешёвые, железо и золото нужны сразу для инструментов —
 * их Удача не ждёт.
 */
public final class MineOres {
    public enum Kind {
        NONE,
        ORDINARY,
        LUCK
    }

    public static final Set<OreKind> LUCK_KINDS = Set.of(OreKind.DIAMOND, OreKind.EMERALD, OreKind.LAPIS);

    /** Кирка в инвентаре духа: слот, годится ли для дропа этой руды, уровень Удачи и Шёлковое касание. */
    public record Pick(int slot, boolean correctForDrops, int fortune, boolean silkTouch) {
        public Pick {
            if (fortune < 0) {
                throw new IllegalArgumentException("Уровень Удачи " + fortune + " отрицательный: зачарование "
                        + "читается из предмета, а не задаётся вручную");
            }
        }
    }

    public sealed interface Decision permits Mine, Leave {}

    /** Добыть; слот пуст — любой подходящий инструмент (выбор BlockBreaker), иначе именно эта кирка. */
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
