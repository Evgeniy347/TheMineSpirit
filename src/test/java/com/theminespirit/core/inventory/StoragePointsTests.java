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

    @Test
    void deposit_FullContainerNear_SkipsToContainerWithSpace() {
        List<StoragePoints.Spot> spots = List.of(
                spot("minecraft:chest", new BlockCoord(0, 64, 2), fullStacks("minecraft:dirt")),
                spot("minecraft:barrel", new BlockCoord(4, 64, 4)));

        StoragePoints.Choice choice =
                StoragePoints.deposit(spots, ORIGIN, new Stack("minecraft:cobblestone", 0, 48, 64));

        assertTrue(choice.found(), "свободная бочка рядом — допустимая точка сдачи, фактически " + choice.log());
        assertEquals(
                new BlockCoord(4, 64, 4),
                choice.point(),
                "полный сундук вплотную не годится: место сдачи выбирается только там, куда предмет влезет "
                        + "(аномалия 3: 86 досок в инвентаре, «рядом нет допустимого контейнера»)");
        assertTrue(
                choice.log().contains("склад поселения") && choice.log().contains("0,64,2"),
                "лог обязан называть склад поселения и отклонённый контейнер с позицией, фактически " + choice.log());
    }

    @Test
    void deposit_NoContainer_RefusesWithRadius() {
        StoragePoints.Choice choice =
                StoragePoints.deposit(List.of(), ORIGIN, new Stack("minecraft:oak_planks", 0, 48, 64));

        assertFalse(
                choice.found(),
                "без контейнера в радиусе сдавать некуда: задание не должно идти в точку без сундука, " + "фактически "
                        + choice.log());
        assertTrue(
                choice.log().contains("не выбрана") && choice.log().contains("склад поселения"),
                "отказ называет, что точки нет и какой склад осмотрели, фактически " + choice.log());
    }

    @Test
    void withdraw_FoodInFarContainer_PicksNearestWithFood() {
        List<StoragePoints.Spot> spots = List.of(
                spot("minecraft:chest", new BlockCoord(0, 64, 2), new Stack("minecraft:oak_planks", 0, 64, 64)),
                spot("minecraft:chest", new BlockCoord(6, 64, 6), new Stack("minecraft:bread", 0, 12, 64)));

        StoragePoints.Choice choice = StoragePoints.withdraw(spots, ORIGIN, "minecraft:bread", 4);

        assertTrue(choice.found(), "хлеб есть в дальнем сундуке — еду берут оттуда, фактически " + choice.log());
        assertEquals(
                new BlockCoord(6, 64, 6),
                choice.point(),
                "точка еды выбирается только там, где лежит еда (аномалия 5: RESTOCK_FOOD падал "
                        + "«рядом нет допустимого контейнера» при голоде 0.85)");
        assertTrue(
                choice.log().contains("склад поселения"),
                "лог еды называет склад поселения, фактически " + choice.log());
    }

    @Test
    void withdraw_NoFood_RefusesNamingCandidates() {
        List<StoragePoints.Spot> spots =
                List.of(spot("minecraft:chest", new BlockCoord(0, 64, 2), new Stack("minecraft:dirt", 0, 64, 64)));

        StoragePoints.Choice choice = StoragePoints.withdraw(spots, ORIGIN, "minecraft:bread", 4);

        assertFalse(choice.found(), "без еды в контейнерах задание еды не ставится, фактически " + choice.log());
        assertTrue(
                choice.log().contains("0,64,2") && choice.log().contains("minecraft:chest"),
                "отказ перечисляет осмотренные контейнеры: позиция и тип, фактически " + choice.log());
    }

    @Test
    void deposit_RejectedContainer_NamesReason() {
        List<StoragePoints.Spot> spots = List.of(
                spot("minecraft:chest", new BlockCoord(0, 64, 2), fullStacks("minecraft:dirt")),
                spot("minecraft:barrel", new BlockCoord(4, 64, 4)));

        StoragePoints.Choice choice =
                StoragePoints.deposit(spots, ORIGIN, new Stack("minecraft:cobblestone", 0, 48, 64));

        assertTrue(
                choice.log().contains("0,64,2") && choice.log().contains("полон"),
                "отклонённый контейнер называется с причиной отказа, а не общей фразой «не подходит», фактически "
                        + choice.log());
    }

    @Test
    void withdraw_ContainerWithoutFood_NamesReason() {
        List<StoragePoints.Spot> spots =
                List.of(spot("minecraft:chest", new BlockCoord(0, 64, 2), new Stack("minecraft:dirt", 0, 64, 64)));

        StoragePoints.Choice choice = StoragePoints.withdraw(spots, ORIGIN, "minecraft:bread", 4);

        assertTrue(
                choice.log().contains("0,64,2 minecraft:chest — нет 'minecraft:bread'"),
                "осмотренный контейнер называется с причиной, почему еду из него не берут, фактически " + choice.log());
    }

    @Test
    void deposit_SameOutcome_KeepsSameStateEvenWhenSpiritMoves() {
        List<StoragePoints.Spot> spots =
                List.of(spot("minecraft:chest", new BlockCoord(0, 64, 2), fullStacks("minecraft:dirt")));

        StoragePoints.Choice first = StoragePoints.deposit(spots, ORIGIN, new Stack("minecraft:oak_planks", 0, 48, 64));
        StoragePoints.Choice second =
                StoragePoints.deposit(spots, new BlockCoord(7, 68, -3), new Stack("minecraft:oak_planks", 0, 48, 64));

        assertEquals(
                first.state(),
                second.state(),
                "смена позиции духа не меняет состояние склада: иначе [storage] повторяется каждые 5 с");
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
