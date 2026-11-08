package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.town.PlotLeveling;
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
                .withFenceBlock("oak_fence")
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
                target, TicketTargetCodec.decode(encoded), "Цель world:backfill обязана пережить перезапуск целиком");
    }

    @Test
    void encodeAndDecode_TransferTarget_KeepsBothSpirits() {
        TicketTarget target = TicketTarget.at(TARGET).withTransfer(SOURCE, RECIPIENT, "bread", 4);

        TicketTarget restored =
                TicketTargetCodec.decode(TicketTargetCodec.encode(target).orElseThrow());

        assertEquals(target, restored, "Передача после перезапуска помнит, кто отдаёт и кто принимает");
        assertEquals(SOURCE, restored.transferFrom(), "Отдающий восстановлен");
        assertEquals(RECIPIENT, restored.transferTo(), "Принимающий восстановлен");
    }

    @Test
    void encode_TargetWithPlanOrStageWork_IsNotPersisted() {
        TicketTarget planned = TicketTarget.at(TARGET).withPlan(new ScriptCall("wait", Map.of()));

        assertTrue(
                TicketTargetCodec.encode(planned).isEmpty(),
                "Цель с планом в NBT не кладётся: дерево плана не сериализуется, лучше не сохранить, чем сохранить криво");
    }

    @Test
    void decode_CoordinateWithoutThreeNumbers_NamesTheBrokenField() {
        Map<String, Object> broken = Map.of("Target", List.of(1, 2));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TicketTargetCodec.decode(broken),
                "Битая точка обязана падать, а не восстанавливаться в (0,0,0)");

        assertTrue(error.getMessage().contains("Target"), "Ошибка называет поле, а не молчит: " + error.getMessage());
    }

    @Test
    void encode_EmptyTarget_IsStillSaved() {
        TicketTarget target = TicketTarget.at(TARGET);

        assertEquals(
                Optional.of(target),
                TicketTargetCodec.encode(target).map(TicketTargetCodec::decode),
                "Даже простая цель (точка) восстанавливается: без неё задание снова уйдёт в дом");
    }
}
