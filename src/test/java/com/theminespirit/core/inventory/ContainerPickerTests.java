package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContainerPickerTests {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);
    private static final Stack COBBLE = new Stack("minecraft:cobblestone", 0, 64, 64);
    private static final Stack PICKAXE = new Stack("minecraft:iron_pickaxe", 0, 1, 1);

    @Test
    void choose_PartialStackInFarContainer_PicksItOverNearEmpty() {
        ContainerSnapshot near = snapshot(0, 64, 2, new Stack("minecraft:oak_planks", 0, 64, 64), null);
        ContainerSnapshot far = snapshot(0, 64, 8, new Stack("minecraft:cobblestone", 0, 10, 64), null);
        List<ContainerSnapshot> containers = List.of(near, far);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, COBBLE);

        assertInstanceOf(ContainerChoice.Chosen.class, choice,
                "сундук с неполной стопкой булыжника обязан быть выбран, даже если он дальше пустого: "
                        + "иначе дух рассыпал бы стак по чужим сундукам");
        assertEquals(1, ((ContainerChoice.Chosen) choice).index(),
                "выбран обязан быть дальний сундук (индекс 1) с неполной стопкой булыжника, а не ближний пустой");
    }

    @Test
    void choose_NearestOfTwoPartialStacks_PicksNearest() {
        ContainerSnapshot near = snapshot(0, 64, 2, new Stack("minecraft:cobblestone", 0, 10, 64), null);
        ContainerSnapshot far = snapshot(0, 64, 8, new Stack("minecraft:cobblestone", 0, 5, 64), null);
        List<ContainerSnapshot> containers = List.of(near, far);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, COBBLE);

        assertEquals(0, ((ContainerChoice.Chosen) choice).index(),
                "из двух неполных стопок того же предмета выбирается ближайший сундук: иначе дух ходил бы дальше");
    }

    @Test
    void choose_FullStacksWithFreeSlot_PreferredOverEmpty() {
        ContainerSnapshot near = snapshot(0, 64, 2, null, null);
        ContainerSnapshot far = snapshot(0, 64, 8, new Stack("minecraft:cobblestone", 0, 64, 64), null);
        List<ContainerSnapshot> containers = List.of(near, far);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, COBBLE);

        assertEquals(1, ((ContainerChoice.Chosen) choice).index(),
                "сундук с полными стопками булыжника и свободным слотом обязан предпочитаться пустому ближнему: "
                        + "предметы одного типа лежат вместе");
    }

    @Test
    void choose_NoKindAnywhere_PicksNearestWithFreeSlot() {
        ContainerSnapshot near = snapshot(0, 64, 2, new Stack("minecraft:oak_planks", 0, 64, 64), null);
        ContainerSnapshot far = snapshot(0, 64, 8, new Stack("minecraft:dirt", 0, 64, 64), null);
        List<ContainerSnapshot> containers = List.of(near, far);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, COBBLE);

        assertEquals(0, ((ContainerChoice.Chosen) choice).index(),
                "если предмета нигде нет, выбирается ближайший контейнер со свободным слотом");
    }

    @Test
    void choose_AllFull_RefusesWithNumbers() {
        ContainerSnapshot first = snapshot(0, 64, 2, new Stack("minecraft:dirt", 0, 64, 64));
        ContainerSnapshot second = snapshot(0, 64, 8, new Stack("minecraft:cobblestone", 0, 64, 64));
        List<ContainerSnapshot> containers = List.of(first, second);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, COBBLE);

        ContainerChoice.Refused refused = assertInstanceOf(ContainerChoice.Refused.class, choice,
                "когда нигде нет места, выбор обязан быть понятным отказом, а не молчаливым первым контейнером");
        assertTrue(refused.reason().contains("2") && refused.reason().contains("minecraft:cobblestone"),
                "отказ обязан называть число контейнеров (2) и предмет, для которого нет места, фактически: "
                        + refused.reason());
    }

    @Test
    void choose_EmptyList_Refuses() {
        ContainerChoice choice = ContainerPicker.choose(List.of(), ORIGIN, COBBLE);

        assertInstanceOf(ContainerChoice.Refused.class, choice,
                "без контейнеров в радиусе выбор обязан быть отказом с причиной");
    }

    @Test
    void choose_NonStackableSameKind_PicksContainerWithSameTool() {
        ContainerSnapshot near = snapshot(0, 64, 2, null, null);
        ContainerSnapshot far = snapshot(0, 64, 8, new Stack("minecraft:iron_pickaxe", 0, 1, 1), null);
        List<ContainerSnapshot> containers = List.of(near, far);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, PICKAXE);

        assertEquals(1, ((ContainerChoice.Chosen) choice).index(),
                "нестакующийся инструмент кладут в сундук, где уже лежит такой же, а не в ближайший пустой: "
                        + "иначе одинаковые кирки разъедутся по разным сундукам");
    }

    @Test
    void choose_NonStackableMissing_FallsBackToNearestFree() {
        ContainerSnapshot near = snapshot(0, 64, 2, null, null);
        ContainerSnapshot far = snapshot(0, 64, 8, null, null);
        List<ContainerSnapshot> containers = List.of(near, far);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, PICKAXE);

        assertEquals(0, ((ContainerChoice.Chosen) choice).index(),
                "если такого инструмента нигде нет, берётся ближайший контейнер со свободным слотом");
    }

    @Test
    void choose_DamagedVariant_NotConfusedWithPristine() {
        Stack damaged = new Stack("minecraft:iron_pickaxe", 1, 1, 1);
        ContainerSnapshot near = snapshot(0, 64, 2, null, null);
        ContainerSnapshot far = snapshot(0, 64, 8, new Stack("minecraft:iron_pickaxe", 0, 1, 1), null);
        List<ContainerSnapshot> containers = List.of(near, far);

        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, damaged);

        assertEquals(0, ((ContainerChoice.Chosen) choice).index(),
                "повреждённая кирка — другой вариант: её нельзя класть к целой, берётся ближайший свободный сундук");
    }

    private static ContainerSnapshot snapshot(int x, int y, int z, Stack... slots) {
        List<Stack> list = new ArrayList<>();
        for (Stack stack : slots) {
            list.add(stack);
        }
        return new ContainerSnapshot(new BlockCoord(x, y, z), list);
    }
}