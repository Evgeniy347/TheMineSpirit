package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphFailureMemory;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphFactInputsTests {
    @Test
    void chestFacts_ContentsAndFreeSlots_AreCounted() {
        ContainerSnapshot snapshot = new ContainerSnapshot(
                new BlockCoord(1, 64, 2),
                Arrays.asList(new Stack("minecraft:oak_log", 0, 5, 64), null, new Stack("minecraft:coal", 0, 2, 64)));

        List<GraphFacts.Chest> chests =
                GraphFactInputs.chestFacts(List.of(snapshot), ignored -> GraphFacts.Reach.UNKNOWN, ignored -> false);

        assertEquals(
                1,
                chests.size(),
                "Один открытый сундук даёт один факт"
                        + " Иначе тест chestFacts_ContentsAndFreeSlots_AreCounted перестанет ловить эту поломку. При смене chestFacts_ContentsAndFreeSlots_AreCounted проверь это утверждение.");
        GraphFacts.Chest chest = chests.get(0);
        assertEquals(
                new GraphFacts.Position(1, 64, 2),
                chest.position(),
                "Позиция сундука переносится"
                        + " Иначе тест chestFacts_ContentsAndFreeSlots_AreCounted перестанет ловить эту поломку. При смене chestFacts_ContentsAndFreeSlots_AreCounted проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:oak_log", 5, "minecraft:coal", 2),
                chest.contents(),
                "Содержимое сундука складывается по предметам"
                        + " Иначе тест chestFacts_ContentsAndFreeSlots_AreCounted перестанет ловить эту поломку. При смене chestFacts_ContentsAndFreeSlots_AreCounted проверь это утверждение.");
        assertEquals(
                1,
                chest.freeSlots(),
                "Свободный слот считается по null-слотам"
                        + " Иначе тест chestFacts_ContentsAndFreeSlots_AreCounted перестанет ловить эту поломку. При смене chestFacts_ContentsAndFreeSlots_AreCounted проверь это утверждение.");
        assertEquals(
                3,
                chest.totalSlots(),
                "Общее число слотов считается по размеру контейнера"
                        + " Иначе тест chestFacts_ContentsAndFreeSlots_AreCounted перестанет ловить эту поломку. При смене chestFacts_ContentsAndFreeSlots_AreCounted проверь это утверждение.");
        assertEquals(
                GraphFacts.Reach.UNKNOWN,
                chest.reach(),
                "Неизвестная достижимость сундука переносится как UNKNOWN"
                        + " Иначе тест chestFacts_ContentsAndFreeSlots_AreCounted перестанет ловить эту поломку. При смене chestFacts_ContentsAndFreeSlots_AreCounted проверь это утверждение.");
    }

    @Test
    void chestFacts_ReachFromWorld_MarksUnreachableChest() {
        ContainerSnapshot snapshot =
                new ContainerSnapshot(new BlockCoord(1, 64, 2), List.of(new Stack("minecraft:bread", 0, 5, 64)));

        GraphFacts.Chest chest = GraphFactInputs.chestFacts(
                        List.of(snapshot), ignored -> GraphFacts.Reach.NO_PATH, ignored -> false)
                .get(0);

        assertEquals(
                GraphFacts.Reach.NO_PATH,
                chest.reach(),
                "Факт достижимости сундука обязан попадать во вход графа: иначе еда в недостижимом сундуке "
                        + "считается выполнимой задачей"
                        + " При смене chestFacts_ReachFromWorld_MarksUnreachableChest проверь это утверждение.");
    }

    @Test
    void chestFacts_RemoteWithdrawal_KeepsPhysicalReachForUnload() {
        ContainerSnapshot snapshot =
                new ContainerSnapshot(new BlockCoord(1, 64, 2), List.of(new Stack("minecraft:bread", 0, 5, 64)));

        GraphFacts.Chest chest = GraphFactInputs.chestFacts(
                        List.of(snapshot), ignored -> GraphFacts.Reach.NO_PATH, pos -> true)
                .get(0);

        assertTrue(
                chest.remoteWithdraw(),
                "Сундук поселения помечается как доступный для дистанционного получения: иначе граф снова "
                        + "считает запасы недоступными. При смене chestFacts_RemoteWithdrawal_KeepsPhysicalReachForUnload "
                        + "проверь ChestWithdrawal.remoteWithdrawal.");
        assertTrue(
                chest.retrievable(),
                "Для получения предмета физический путь не требуется: иначе удалённый сундук не закрывает "
                        + "нехватку еды и материалов. При смене chestFacts_RemoteWithdrawal_KeepsPhysicalReachForUnload "
                        + "проверь GraphFacts.Chest.retrievable.");
        assertEquals(
                GraphFacts.Reach.NO_PATH,
                chest.reach(),
                "Физическая достижимость остаётся NO_PATH для выгрузки: иначе граф отправит духа сдавать "
                        + "вещи в недостижимый сундук. При смене chestFacts_RemoteWithdrawal_KeepsPhysicalReachForUnload "
                        + "проверь StorePost и OverloadPost.");
    }

    @Test
    void reach_KnownPathFailure_MarksChestUnreachable() {
        BlockCoord failed = new BlockCoord(1, 64, 2);
        Set<String> failures = Set.of(GraphFailureMemory.target(failed.x(), failed.y(), failed.z()));

        assertEquals(
                GraphFacts.Reach.NO_PATH,
                GraphFactInputs.reach(failures, failed, true, true),
                "Сундук с недавним отказом пути больше не считается достижимым: иначе снаряжение, еда и "
                        + "переносы снова выдадут ту же недоступную цель и провалятся по тайм-ауту. При смене "
                        + "GraphFactInputs.reach проверь GearRules.equipUpgrade, StorePost.transfer и ChestReach.");
        assertEquals(
                GraphFacts.Reach.PATH,
                GraphFactInputs.reach(failures, new BlockCoord(3, 64, 2), true, true),
                "Соседний сундук без отказа пути остаётся достижимым: иначе память одной точки заблокирует "
                        + "весь склад и лишит духов еды и инструментов. При смене GraphFactInputs.reach проверь "
                        + "GraphFailureMemory.target и живой сундук рядом с воронкой.");
    }

    @Test
    void resourceFacts_NearestTag_KeepsDistanceAndUnknownReach() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphSurroundings.Around around = new GraphSurroundings.Around(
                Set.of("tree:oak"),
                Set.of(),
                Map.of(),
                Optional.empty(),
                Map.of("tree:oak", new BlockCoord(5, 64, 6)),
                Map.of("tree:oak", 4));

        List<GraphFacts.Resource> resources = GraphFactInputs.resourceFacts(around, graph);

        assertEquals(
                1,
                resources.size(),
                "Один тег с координатой даёт один факт ресурса"
                        + " Иначе тест resourceFacts_NearestTag_KeepsDistanceAndUnknownReach перестанет ловить эту поломку. При смене resourceFacts_NearestTag_KeepsDistanceAndUnknownReach проверь это утверждение.");
        GraphFacts.Resource resource = resources.get(0);
        assertEquals(
                "tree:oak",
                resource.tag(),
                "Тег ресурса переносится"
                        + " Иначе тест resourceFacts_NearestTag_KeepsDistanceAndUnknownReach перестанет ловить эту поломку. При смене resourceFacts_NearestTag_KeepsDistanceAndUnknownReach проверь это утверждение.");
        assertEquals(
                new GraphFacts.Position(5, 64, 6),
                resource.position(),
                "Координата ресурса переносится"
                        + " Иначе тест resourceFacts_NearestTag_KeepsDistanceAndUnknownReach перестанет ловить эту поломку. При смене resourceFacts_NearestTag_KeepsDistanceAndUnknownReach проверь это утверждение.");
        assertEquals(
                4,
                resource.distance(),
                "Расстояние до ресурса переносится в блоках"
                        + " Иначе тест resourceFacts_NearestTag_KeepsDistanceAndUnknownReach перестанет ловить эту поломку. При смене resourceFacts_NearestTag_KeepsDistanceAndUnknownReach проверь это утверждение.");
        assertEquals(
                GraphFacts.Reach.UNKNOWN,
                resource.reach(),
                "Достижимость пока неизвестна модели"
                        + " Иначе тест resourceFacts_NearestTag_KeepsDistanceAndUnknownReach перестанет ловить эту поломку. При смене resourceFacts_NearestTag_KeepsDistanceAndUnknownReach проверь это утверждение.");
        assertEquals(
                graph.costs().travel(4),
                resource.travelTicks(),
                "Время дороги считается теми же costs, что и в планировщике"
                        + " Иначе тест resourceFacts_NearestTag_KeepsDistanceAndUnknownReach перестанет ловить эту поломку. При смене resourceFacts_NearestTag_KeepsDistanceAndUnknownReach проверь это утверждение.");
    }
}
