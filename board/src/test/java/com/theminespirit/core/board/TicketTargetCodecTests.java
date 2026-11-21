package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTargetCodecTests {
    private static final BlockCoord TARGET = new BlockCoord(10, 64, -5);
    private static final BlockCoord FROM = new BlockCoord(1, 2, 3);
    private static final BlockCoord TO = new BlockCoord(4, 5, 6);
    private static final BlockCoord DEPOSIT = new BlockCoord(7, 8, 9);
    private static final UUID SOURCE = new UUID(0, 21);
    private static final UUID RECIPIENT = new UUID(0, 22);

    @Test
    void encodeAndDecode_RichTarget_KeepsEveryPersistedField() {
        PlotLeveling leveling = new PlotLeveling(
                List.of(new PlotLeveling.Box(1, 60, 2, 3, 61, 4)),
                List.of(new PlotLeveling.Box(5, 60, 6, 5, 60, 6)),
                "cobblestone",
                List.of(new PlotLeveling.Box(7, 61, 8, 7, 61, 8)));
        TicketTarget target = TicketTarget.at(TARGET)
                .withScheme("hut.json")
                .withLureItem("carrot")
                .withSupply("coal", 12)
                .withCraterBlocks(5)
                .withToolCount(2)
                .withSpecies(Species.COW)
                .withRelocate(FROM, TO)
                .withDrops(List.of("dirt", "cobblestone"))
                .withCraft("torch", 9)
                .withWood("oak")
                .withBuilding("hut")
                .withLeveling(leveling)
                .withDeposit(DEPOSIT);

        Map<String, Object> encoded = TicketTargetCodec.encode(target).orElseThrow();

        assertEquals(
                target,
                TicketTargetCodec.decode(encoded),
                "Цель world:backfill обязана пережить перезапуск целиком"
                        + " Иначе тест encodeAndDecode_RichTarget_KeepsEveryPersistedField перестанет ловить эту поломку. При смене encodeAndDecode_RichTarget_KeepsEveryPersistedField проверь это утверждение.");
    }

    @Test
    void encodeAndDecode_FenceCells_KeepsEveryCellAndBlock() {
        List<BlockCoord> cells = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            cells.add(new BlockCoord(100 + index, 64, -30 + index));
            blocks.add(index % 2 == 0 ? "minecraft:oak_fence" : "minecraft:cobblestone_wall");
        }
        TicketTarget target = TicketTarget.at(cells.get(0)).withFenceCells(cells, blocks);

        TicketTarget restored =
                TicketTargetCodec.decode(TicketTargetCodec.encode(target).orElseThrow());

        assertEquals(
                target,
                restored,
                "Задание ограды после перезапуска обязано помнить все проломы и их блоки"
                        + " Иначе тест encodeAndDecode_FenceCells_KeepsEveryCellAndBlock перестанет ловить эту поломку. При смене encodeAndDecode_FenceCells_KeepsEveryCellAndBlock проверь это утверждение.");
    }

    @Test
    void decode_LegacyFenceKey_KeepsBlock() {
        TicketTarget restored = TicketTargetCodec.decode(
                Map.of("Target", List.of(TARGET.x(), TARGET.y(), TARGET.z()), "Fence", "oak_fence"));

        assertEquals(
                "oak_fence",
                restored.fenceBlock(),
                "Цель старой заявки ограды помнит блок: иначе пролом нечем чинить"
                        + " При смене decode_LegacyFenceKey_KeepsBlock проверь это утверждение.");
    }

    @Test
    void encodeAndDecode_TransferTarget_KeepsBothSpirits() {
        TicketTarget target = TicketTarget.at(TARGET).withTransfer(SOURCE, RECIPIENT, "bread", 4);

        TicketTarget restored =
                TicketTargetCodec.decode(TicketTargetCodec.encode(target).orElseThrow());

        assertEquals(
                target,
                restored,
                "Передача после перезапуска помнит, кто отдаёт и кто принимает"
                        + " Иначе тест encodeAndDecode_TransferTarget_KeepsBothSpirits перестанет ловить эту поломку. При смене encodeAndDecode_TransferTarget_KeepsBothSpirits проверь это утверждение.");
        assertEquals(
                SOURCE,
                restored.transferFrom(),
                "Отдающий восстановлен"
                        + " Иначе тест encodeAndDecode_TransferTarget_KeepsBothSpirits перестанет ловить эту поломку. При смене encodeAndDecode_TransferTarget_KeepsBothSpirits проверь это утверждение.");
        assertEquals(
                RECIPIENT,
                restored.transferTo(),
                "Принимающий восстановлен"
                        + " Иначе тест encodeAndDecode_TransferTarget_KeepsBothSpirits перестанет ловить эту поломку. При смене encodeAndDecode_TransferTarget_KeepsBothSpirits проверь это утверждение.");
    }

    @Test
    void encode_TargetWithPlanOrStageWork_IsNotPersisted() {
        TicketTarget planned = TicketTarget.at(TARGET).withPlan(new ScriptCall("wait", Map.of()));

        assertTrue(
                TicketTargetCodec.encode(planned).isEmpty(),
                "Цель с планом в NBT не кладётся: дерево плана не сериализуется, лучше не сохранить, чем сохранить криво"
                        + " Иначе тест encode_TargetWithPlanOrStageWork_IsNotPersisted перестанет ловить эту поломку. При смене encode_TargetWithPlanOrStageWork_IsNotPersisted проверь это утверждение.");
    }

    @Test
    void decode_CoordinateWithoutThreeNumbers_NamesTheBrokenField() {
        Map<String, Object> broken = Map.of("Target", List.of(1, 2));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TicketTargetCodec.decode(broken),
                "Битая точка обязана падать, а не восстанавливаться в (0,0,0)"
                        + " Иначе тест decode_CoordinateWithoutThreeNumbers_NamesTheBrokenField перестанет ловить эту поломку. При смене decode_CoordinateWithoutThreeNumbers_NamesTheBrokenField проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Target"),
                "Ошибка называет поле, а не молчит: " + error.getMessage()
                        + " Иначе тест decode_CoordinateWithoutThreeNumbers_NamesTheBrokenField перестанет ловить эту поломку. При смене decode_CoordinateWithoutThreeNumbers_NamesTheBrokenField проверь это утверждение.");
    }

    @Test
    void encode_EmptyTarget_IsStillSaved() {
        TicketTarget target = TicketTarget.at(TARGET);

        assertEquals(
                Optional.of(target),
                TicketTargetCodec.encode(target).map(TicketTargetCodec::decode),
                "Даже простая цель (точка) восстанавливается: без неё задание снова уйдёт в дом"
                        + " Иначе тест encode_EmptyTarget_IsStillSaved перестанет ловить эту поломку. При смене encode_EmptyTarget_IsStillSaved проверь это утверждение.");
    }
}
