package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.policy.api.PolicyId;
import com.theminespirit.core.town.api.PlanBuilding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettlementBreakProtectionTests {
    private static final Path OBSERVE23 = Path.of("src/test/resources/live/observe23/plot-edits.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.readClasspath();
    }

    @Test
    void own_SpiritWallTorchOutsidePlan_Allowed() {
        assertEquals(
                new BlockProtection.Allowed(),
                SettlementBreakProtection.own(protection, "minecraft:wall_torch", false),
                "Свой факел духа вне клетки плана (шахта -189,60,-507) обязан ломаться: решение принимает "
                        + "ProtectionGate.checkBreak, а не отдельная проверка с другим флагом плана, иначе дух "
                        + "не переставит свой свет. При смене own проверь факел вне плана");
    }

    @Test
    void own_ContainerOutsidePlan_StillRefused() {
        assertInstanceOf(
                BlockProtection.Refused.class,
                SettlementBreakProtection.own(protection, "minecraft:chest", false),
                "Свой сундук всё равно нельзя: контейнеры запрещены для gather, иначе дух разберёт склад. "
                        + "При смене own проверь chest от имени духа вне плана");
    }

    @Test
    void own_WallTorchInProtectedZone_StillRefused() {
        assertInstanceOf(
                BlockProtection.Refused.class,
                SettlementBreakProtection.own(protection, "minecraft:wall_torch", true),
                "Защищённая зона сильнее метки духа: факел в чужой зоне не ломают, иначе дух чистит чужие "
                        + "механизмы. При смене own проверь свой факел в защищённой зоне");
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
                        + "−171..−167,−489..−486): снос обязан отказать, иначе уборка разбирает готовую постройку. "
                        + "При смене decide проверь доски вне клетки плана");
        assertEquals(
                PolicyId.PLAYER_BUILDINGS,
                refused.policy(),
                "Отказ обязан помечать постройку игрока, а не безымянную зону: по коду видно, почему не ломаем. "
                        + "При смене порядка правил проверь PolicyId PLAYER_BUILDINGS");
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
                "Одинокая доска среди травы — висящий остаток, а не постройка: уборка обязана уметь её снять, "
                        + "иначе мусор копится. При смене подсчёта соседей проверь доску без искусственного окружения");
    }

    @Test
    void demolish_OwnHousePlanCell_Allowed() {
        BlockProtection.Decision decision = SettlementBreak.decide(
                protection,
                new SettlementBreak.Facts(
                        SettlementBreak.Origin.SPIRIT,
                        true,
                        true,
                        "minecraft:oak_planks",
                        false,
                        false,
                        PlanBuilding.HOUSE,
                        PlanBuilding.HOUSE),
                SettlementBreak.Intent.DEMOLISH,
                () -> natural(BlockProtection.NEIGHBOUR_COUNT));

        assertEquals(
                new BlockProtection.Allowed(),
                decision,
                "Ремонт дома обязан снимать свои клетки плана: без этого повреждённую хижину не восстановить. "
                        + "При смене Intent.DEMOLISH проверь клетку HOUSE, принадлежащую дому");
    }

    @Test
    void demolish_FenceOverHousePlanCell_Refused() {
        BlockProtection.Decision decision = SettlementBreak.decide(
                protection,
                new SettlementBreak.Facts(
                        SettlementBreak.Origin.SPIRIT,
                        true,
                        true,
                        "minecraft:oak_planks",
                        false,
                        false,
                        PlanBuilding.HOUSE,
                        PlanBuilding.FENCE),
                SettlementBreak.Intent.DEMOLISH,
                () -> natural(BlockProtection.NEIGHBOUR_COUNT));

        BlockProtection.Refused refused = assertInstanceOf(
                BlockProtection.Refused.class,
                decision,
                "Забор не имеет права расчищать клетку дома: в observe23 он снял стену хижины "
                        + "в (-178..-176,69,-505), аудит 100→94; забор обязан обойти дом или ждать. "
                        + "При смене проверки чужих построек сверь HOUSE против FENCE");
        assertEquals(
                PolicyId.PROTECTED_BLOCKS,
                refused.policy(),
                "Отказ обязан помечать защиту блока плана: по коду видно, почему забор не тронул дом. "
                        + "При смене порядка правил проверь PolicyId PROTECTED_BLOCKS");
        assertTrue(
                refused.reason().contains("HOUSE") && refused.reason().contains("FENCE"),
                "Причина отказа обязана называть чужую постройку и виновника, иначе разбор инцидента ищет " + "наугад: "
                        + refused.reason() + ". При смене текста проверь HOUSE и FENCE в причине");
    }

    @Test
    void demolish_LiveObserve23FenceEdits_Refused() throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(OBSERVE23, StandardCharsets.UTF_8));
        int checked = 0;
        for (JsonNode event : root.get("events")) {
            BlockProtection.Decision decision = SettlementBreak.decide(
                    protection,
                    new SettlementBreak.Facts(
                            SettlementBreak.Origin.SPIRIT,
                            true,
                            true,
                            event.get("planBlock").asText(),
                            false,
                            false,
                            PlanBuilding.HOUSE,
                            PlanBuilding.FENCE),
                    SettlementBreak.Intent.DEMOLISH,
                    () -> natural(BlockProtection.NEIGHBOUR_COUNT));
            assertInstanceOf(
                    BlockProtection.Refused.class,
                    decision,
                    "Живая фикстура observe23: забор снял "
                            + event.get("planBlock").asText() + " в "
                            + event.get("cell") + " (дух " + event.get("who").asText() + ", задание "
                            + event.get("task").asText() + "); клетка дома чужая для забора, иначе дома теряют стены. "
                            + "При смене фикстуры observe23 проверь каждое событие забора");
            checked++;
        }
        assertTrue(
                checked >= 5,
                "В фикстуре observe23 меньше 5 событий [plot-edit] забора: тест перестал повторять живой прогон, "
                        + "проверено " + checked + ". При смене фикстуры проверь, что событий забора не меньше 5");
    }

    @Test
    void demolish_FenceOverHousePlacedByPlayer_Refused() {
        BlockProtection.Decision decision = SettlementBreak.decide(
                protection,
                new SettlementBreak.Facts(
                        SettlementBreak.Origin.PLAYER,
                        true,
                        true,
                        "minecraft:oak_planks",
                        false,
                        false,
                        PlanBuilding.HOUSE,
                        PlanBuilding.FENCE),
                SettlementBreak.Intent.DEMOLISH,
                () -> natural(BlockProtection.NEIGHBOUR_COUNT));

        assertInstanceOf(
                BlockProtection.Refused.class,
                decision,
                "Забор не имеет права снимать стену дома, даже если доску поставил игрок: клетка принадлежит плану "
                        + "дома, а не забора, иначе ремонт забора разбирает хижину. При смене проверь Origin.PLAYER");
    }

    @Test
    void demolish_UnknownActionOverHouse_Refused() {
        BlockProtection.Decision decision = SettlementBreak.decide(
                protection,
                new SettlementBreak.Facts(
                        SettlementBreak.Origin.UNKNOWN,
                        true,
                        true,
                        "minecraft:grass_block",
                        false,
                        false,
                        PlanBuilding.HOUSE,
                        PlanBuilding.NONE),
                SettlementBreak.Intent.DEMOLISH,
                () -> natural(BlockProtection.NEIGHBOUR_COUNT));

        assertInstanceOf(
                BlockProtection.Refused.class,
                decision,
                "Уборка без своей постройки тоже не имеет права снимать клетку дома: в observe23 такая расчистка "
                        + "сняла oak_stairs в (-178,69,-510), и дом потерял блок. При смене проверь Origin.UNKNOWN с NONE");
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
