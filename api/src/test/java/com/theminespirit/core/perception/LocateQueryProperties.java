package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class LocateQueryProperties {
    @Provide
    Arbitrary<String> species() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(12);
    }

    @Property
    void tree_AnyValidSpeciesAndRadius_KeepsBothUnchanged(
            @ForAll("species") String species,
            @ForAll @IntRange(min = ScanRadius.MIN, max = ScanRadius.MAX) int radius) {
        LocateQuery query = LocateQuery.tree(species, new ScanRadius(radius));

        assertEquals(
                LocateType.TREE,
                query.type(),
                "фабрика tree обязана строить поиск TREE"
                        + " Иначе тест tree_AnyValidSpeciesAndRadius_KeepsBothUnchanged перестанет ловить эту поломку. При смене tree_AnyValidSpeciesAndRadius_KeepsBothUnchanged проверь это утверждение.");
        assertEquals(
                species,
                query.species(),
                "вид дерева обязан остаться тем, что задан"
                        + " Иначе тест tree_AnyValidSpeciesAndRadius_KeepsBothUnchanged перестанет ловить эту поломку. При смене tree_AnyValidSpeciesAndRadius_KeepsBothUnchanged проверь это утверждение.");
        assertEquals(
                radius,
                query.radius().value(),
                "радиус поиска обязан остаться тем, что задан"
                        + " Иначе тест tree_AnyValidSpeciesAndRadius_KeepsBothUnchanged перестанет ловить эту поломку. При смене tree_AnyValidSpeciesAndRadius_KeepsBothUnchanged проверь это утверждение.");
    }

    @Property
    void container_SpeciesGivenForNonTree_IsRejected(@ForAll("species") String species) {
        if (LocateQuery.ANY_SPECIES.equals(species)) {
            return;
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocateQuery(LocateType.CONTAINER, IdFilter.matchAll(), species, ScanRadius.DEFAULT),
                "вид '" + species + "' у поиска контейнера обязан отклоняться: вид применим только к дереву"
                        + " Иначе тест container_SpeciesGivenForNonTree_IsRejected перестанет ловить эту поломку. При смене container_SpeciesGivenForNonTree_IsRejected проверь это утверждение.");
    }
}
