package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettlementBreakProtectionTests {
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.load();
    }

    @Test
    void own_SpiritWallTorchOutsidePlan_Allowed() {
        assertEquals(
                new BlockProtection.Allowed(),
                SettlementBreakProtection.own(protection, "minecraft:wall_torch", false),
                "Свой факел духа вне клетки плана (шахта -189,60,-507) обязан ломаться: решение принимает "
                        + "ProtectionGate.checkBreak, а не отдельная проверка с другим флагом плана");
    }

    @Test
    void own_ContainerOutsidePlan_StillRefused() {
        assertInstanceOf(
                BlockProtection.Refused.class,
                SettlementBreakProtection.own(protection, "minecraft:chest", false),
                "Свой сундук всё равно нельзя: контейнеры запрещены для gather, иначе дух разберёт склад");
    }

    @Test
    void own_WallTorchInProtectedZone_StillRefused() {
        assertInstanceOf(
                BlockProtection.Refused.class,
                SettlementBreakProtection.own(protection, "minecraft:wall_torch", true),
                "Защищённая зона сильнее метки духа: факел в чужой зоне не ломают");
    }

    @Test
    void plot_PlayerShackOutsidePlan_RefusesBreakLikePlayerBuilding() {
        SettlementBreak.Facts facts = new SettlementBreak.Facts(
                SettlementBreak.Origin.UNKNOWN, true, false, "minecraft:oak_planks", false, false);

        BlockProtection.Decision decision =
                SettlementBreak.decide(protection, facts, () -> artificial(BlockProtection.NEIGHBOUR_COUNT));

        BlockProtection.Refused refused = assertInstanceOf(
                BlockProtection.Refused.class,
                decision,
                "Доски крыши постройки игрока вне плана окружены искусственными блоками (observe14: хижина "
                        + "−171..−167,−489..−486): снос обязан отказать, иначе уборка разбирает готовую постройку");
        assertEquals(
                PolicyId.PLAYER_BUILDINGS,
                refused.policy(),
                "Отказ обязан помечать постройку игрока, а не безымянную зону: по коду видно, почему не ломаем");
    }

    @Test
    void plot_LonePlankOnNaturalGround_RemainsAllowed() {
        SettlementBreak.Facts facts = new SettlementBreak.Facts(
                SettlementBreak.Origin.UNKNOWN, true, false, "minecraft:oak_planks", false, false);

        BlockProtection.Decision decision =
                SettlementBreak.decide(protection, facts, () -> natural(BlockProtection.NEIGHBOUR_COUNT));

        assertEquals(
                new BlockProtection.Allowed(),
                decision,
                "Одинокая доска среди травы — висящий остаток, а не постройка: уборка обязана уметь её снять");
    }

    private static List<String> artificial(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "minecraft:oak_planks")
                .toList();
    }

    private static List<String> natural(int count) {
        return IntStream.range(0, count).mapToObj(index -> "minecraft:air").toList();
    }
}
