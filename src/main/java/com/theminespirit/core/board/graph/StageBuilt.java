package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class StageBuilt {
    public static final String ORCHARD = "orchard_oak";
    private static final int YARD_CHESTS = 2;
    private static final Map<String, Predicate<SettlementFacts>> BUILT = built();

    private StageBuilt() {}

    public static Set<String> ids() {
        return BUILT.keySet();
    }

    public static Map<String, Built> of(SettlementFacts facts) {
        Objects.requireNonNull(facts, "Факты поселения не могут быть null: по ним видно, что построено");
        Map<String, Built> states = new LinkedHashMap<>();
        BUILT.forEach((id, test) -> states.put(id, test.test(facts) ? Built.BUILT : Built.PLANNED));
        return Collections.unmodifiableMap(states);
    }

    private static Map<String, Predicate<SettlementFacts>> built() {
        Map<String, Predicate<SettlementFacts>> built = new LinkedHashMap<>();
        built.put("crafting_table", facts -> done(facts, BoardCase.CRAFTING_STATION) || facts.craftingStation());
        built.put(
                "clearing",
                facts -> (done(facts, BoardCase.CLEAR_GROUNDS) || facts.groundsCleared())
                        && (done(facts, BoardCase.CLEAR_GROUNDS) || facts.plotLevel()));
        built.put("chests", facts -> facts.chests() >= YARD_CHESTS);
        built.put("mine", SettlementFacts::mineEntrance);
        built.put("furnace", facts -> done(facts, BoardCase.CRAFT_FURNACE) || facts.furnace());
        built.put("brewing_stand", facts -> done(facts, BoardCase.CRAFT_BREWING_STAND));
        built.put("lighting", SettlementFacts::lighting);
        built.put("fence", SettlementFacts::fence);
        built.put("hut", SettlementFacts::houses);
        built.put(ORCHARD, SettlementFacts::orchard);
        built.put("streets", SettlementFacts::streets);
        built.put("smithing_table", facts -> done(facts, BoardCase.CRAFT_SMITHING_TABLE) || facts.smithingTable());
        built.put("anvil", facts -> done(facts, BoardCase.CRAFT_ANVIL) || facts.anvil());
        return Collections.unmodifiableMap(built);
    }

    private static boolean done(SettlementFacts facts, BoardCase boardCase) {
        return facts.done().contains(boardCase);
    }
}
