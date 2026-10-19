package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.perception.LocateQuery;
import com.theminespirit.core.perception.LocateType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocateParamsTests {
    @Test
    void block_FilterAndRadius_BuildBlockQuery() {
        LocateQuery query = LocateParams.block(Map.of("filter", List.of("*_ore"), "radius", 9));

        assertEquals(LocateType.BLOCK, query.type(), "locate_block обязан строить поиск блока");
        assertEquals(9, query.radius().value(), "радиус поиска обязан совпасть с параметром плана");
        assertEquals(
                List.of("minecraft:*_ore"),
                query.filter().patterns(),
                "фильтр поиска обязан совпасть с параметром плана: иначе вернётся не тот блок");
    }

    @Test
    void block_EmptyOrWildcardFilter_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LocateParams.block(Map.of("filter", List.of(), "radius", 8)),
                "поиск блока без фильтра обязан отклоняться: ближайший блок «любого вида» — клетка под духом");
        assertThrows(
                IllegalArgumentException.class,
                () -> LocateParams.block(Map.of("filter", List.of("*"), "radius", 8)),
                "фильтр со звёздочкой равен «всё» и обязан отклоняться так же, как пустой");
    }

    @Test
    void tree_SpeciesAndRadius_BuildTreeQuery() {
        LocateQuery query = LocateParams.tree(Map.of("species", "birch", "radius", 6));

        assertEquals(LocateType.TREE, query.type(), "locate_tree обязан строить поиск дерева");
        assertEquals("birch", query.species(), "порода обязана совпасть с параметром плана");
        assertEquals(
                List.of("birch"),
                LocateParams.speciesOf(query).stream().map(TreeSpecies::id).toList(),
                "порода birch обязана давать только берёзу, иначе поиск вернёт чужое дерево");
    }

    @Test
    void tree_AnySpecies_CoversEveryKnownSpecies() {
        LocateQuery query = LocateParams.tree(Map.of("species", "any", "radius", 6));

        assertEquals(
                TreeSpecies.ALL,
                LocateParams.speciesOf(query),
                "порода any обязана охватывать все известные породы: иначе дерево редкой породы не найдётся");
    }

    @Test
    void tree_UnknownSpecies_IsRejectedWithKnownNames() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LocateParams.tree(Map.of("species", "baobab", "radius", 6)),
                "неизвестная порода обязана отклоняться до запуска поиска");

        assertEquals(
                true,
                error.getMessage().contains("oak"),
                "сообщение обязано перечислить допустимые породы, сейчас: " + error.getMessage());
    }

    @Test
    void container_Radius_BuildContainerQuery() {
        LocateQuery query = LocateParams.container(Map.of("radius", 4));

        assertEquals(LocateType.CONTAINER, query.type(), "locate_container обязан строить поиск контейнера");
        assertEquals(4, query.radius().value(), "радиус поиска обязан совпасть с параметром плана");
    }
}
