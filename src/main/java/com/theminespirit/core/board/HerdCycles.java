package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Реестр циклов животных по видам: у каждого вида свой цикл capture → pen → lure → dismantle,
 * свои id заданий, свой загон и своя приманка. Циклы не мешают друг другу: гибель коровы
 * снимает «приманить» коровы, а не свиньи.
 */
public final class HerdCycles {
    private final Board board;
    private final Map<Species, AnimalLifecycle> cycles = new EnumMap<>(Species.class);

    public HerdCycles(Board board) {
        this.board = Objects.requireNonNull(board, "board не может быть null: циклы ставят и "
                + "снимают задания через доску");
    }

    public AnimalLifecycle cycle(Species species) {
        Objects.requireNonNull(species, "Вид цикла не может быть null: без него непонятно, "
                + "чей цикл открывать");
        return cycles.computeIfAbsent(species, found -> new AnimalLifecycle(board, found));
    }

    public Set<Species> species() {
        return Set.copyOf(cycles.keySet());
    }
}
