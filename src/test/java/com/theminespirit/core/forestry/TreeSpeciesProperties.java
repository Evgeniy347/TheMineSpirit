package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TreeSpeciesProperties {
    @Property
    void ofLog_AnySpeciesLogId_ReturnsThatSpecies(@ForAll("species") TreeSpecies species) {
        assertEquals(Optional.of(species), TreeSpecies.ofLog(species.logId()),
                "ofLog(s.logId) обязан вернуть ровно s для каждой породы: иначе поиск разойдётся с таблицей пород "
                        + "и найденное бревно не распознается как дерево");
    }

    @Provide
    Arbitrary<TreeSpecies> species() {
        return Arbitraries.of(TreeSpecies.ALL);
    }
}