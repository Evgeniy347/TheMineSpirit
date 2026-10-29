package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoragePointsTests {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);
    private static final int RADIUS = 16;

    @Test
    void deposit_FullContainerNear_SkipsToContainerWithSpace() {
        List<StoragePoints.Spot> spots = List.of(
                spot("minecraft:chest", new BlockCoord(0, 64, 2), fullStacks("minecraft:dirt")),
                spot("minecraft:barrel", new BlockCoord(4, 64, 4)));

        StoragePoints.Choice choice =
                StoragePoints.deposit(spots, ORIGIN, new Stack("minecraft:cobblestone", 0, 48, 64), RADIUS);

        assertTrue(choice.found(), "свободная бочка рядом — допустимая точка сдачи, фактически " + choice.log());
        assertEquals(
                new BlockCoord(4, 64, 4),
                choice.point(),
                "полный сундук вплотную не годится: место сдачи выбирается только там, куда предмет влезет "
                        + "(аномалия 3: 86 досок в инвентаре, «рядом нет допустимого контейнера»)");
        assertTrue(
                choice.log().contains("радиус " + RADIUS) && choice.log().contains("0,64,2"),
                "лог обязан называть радиус поиска и отклонённый контейнер с позицией, фактически " + choice.log());
    }

    @Test
    void deposit_NoContainer_RefusesWithRadius() {
        StoragePoints.Choice choice =
                StoragePoints.deposit(List.of(), ORIGIN, new Stack("minecraft:oak_planks", 0, 48, 64), RADIUS);

        assertFalse(
                choice.found(),
                "без контейнера в радиусе сдавать некуда: задание не должно идти в точку без сундука, " + "фактически "
                        + choice.log());
        assertTrue(
                choice.log().contains("не выбрана") && choice.log().contains("радиус " + RADIUS),
                "отказ называет, что точки нет и какой радиус искали, фактически " + choice.log());
    }

    @Test
    void withdraw_FoodInFarContainer_PicksNearestWithFood() {
        List<StoragePoints.Spot> spots = List.of(
                spot("minecraft:chest", new BlockCoord(0, 64, 2), new Stack("minecraft:oak_planks", 0, 64, 64)),
                spot("minecraft:chest", new BlockCoord(6, 64, 6), new Stack("minecraft:bread", 0, 12, 64)));

        StoragePoints.Choice choice = StoragePoints.withdraw(spots, ORIGIN, "minecraft:bread", 4, RADIUS);

        assertTrue(choice.found(), "хлеб есть в дальнем сундуке — еду берут оттуда, фактически " + choice.log());
        assertEquals(
                new BlockCoord(6, 64, 6),
                choice.point(),
                "точка еды выбирается только там, где лежит еда (аномалия 5: RESTOCK_FOOD падал "
                        + "«рядом нет допустимого контейнера» при голоде 0.85)");
        assertTrue(
                choice.log().contains("радиус " + RADIUS),
                "лог еды называет радиус поиска, фактически " + choice.log());
    }

    @Test
    void withdraw_NoFood_RefusesNamingCandidates() {
        List<StoragePoints.Spot> spots =
                List.of(spot("minecraft:chest", new BlockCoord(0, 64, 2), new Stack("minecraft:dirt", 0, 64, 64)));

        StoragePoints.Choice choice = StoragePoints.withdraw(spots, ORIGIN, "minecraft:bread", 4, RADIUS);

        assertFalse(choice.found(), "без еды в контейнерах задание еды не ставится, фактически " + choice.log());
        assertTrue(
                choice.log().contains("0,64,2") && choice.log().contains("minecraft:chest"),
                "отказ перечисляет осмотренные контейнеры: позиция и тип, фактически " + choice.log());
    }

    @Test
    void count_TwoMatchingStacks_SumsCounts() {
        ContainerSnapshot snapshot = snapshot(
                new BlockCoord(0, 64, 2),
                new Stack("minecraft:bread", 0, 3, 64),
                new Stack("minecraft:bread", 0, 4, 64));

        assertEquals(7, StoragePoints.count(snapshot, "minecraft:bread"), "считаются все стопки одного предмета");
    }

    private static StoragePoints.Spot spot(String type, BlockCoord pos, Stack... stacks) {
        return new StoragePoints.Spot(pos, type, snapshot(pos, stacks));
    }

    private static Stack[] fullStacks(String item) {
        Stack[] stacks = new Stack[27];
        for (int slot = 0; slot < stacks.length; slot++) {
            stacks[slot] = new Stack(item, 0, 64, 64);
        }
        return stacks;
    }

    private static ContainerSnapshot snapshot(BlockCoord pos, Stack... stacks) {
        List<Stack> slots = new ArrayList<>();
        for (Stack stack : stacks) {
            slots.add(stack);
        }
        while (slots.size() < 27) {
            slots.add(null);
        }
        return new ContainerSnapshot(pos, slots);
    }
}
