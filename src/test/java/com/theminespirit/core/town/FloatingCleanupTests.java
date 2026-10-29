package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class FloatingCleanupTests {
    private static final BlockCoord AT = new BlockCoord(1, 70, 1);

    @Test
    void decide_PlayerBuilding_IsProtected() {
        assertEquals(
                FloatingCleanup.Action.SKIP_PROTECTED,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.FENCE), new FloatingCleanup.Rooms(true, true, true, false)),
                "Постройку игрока уборка не трогает даже с готовым двором: защита старше порядка");
    }

    @Test
    void decide_RegisteredChest_WaitsForRegistry() {
        assertEquals(
                FloatingCleanup.Action.SKIP_REGISTERED,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.REGISTERED_CHEST),
                        new FloatingCleanup.Rooms(true, false, false, false)),
                "Сундук из реестра сначала освобождает OldChests: уборка не снимает его с содержимым");
    }

    @Test
    void decide_EmptyChestWithoutYard_IsLeft() {
        assertEquals(
                FloatingCleanup.Action.SKIP_WAIT,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.CHEST), new FloatingCleanup.Rooms(false, false, true, false)),
                "Без сундуков двора переносить некуда: уборка ждёт, а не теряет вещи");
    }

    @Test
    void decide_EmptyChestWithYard_IsRemoved() {
        assertEquals(
                FloatingCleanup.Action.REMOVE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.CHEST), new FloatingCleanup.Rooms(true, false, true, false)),
                "Пустой сундук вне плана снимают: содержимого, которое надо спасать, нет");
    }

    @Test
    void decide_FullChestWithYard_IsDrained() {
        assertEquals(
                FloatingCleanup.Action.DRAIN_CHEST,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.CHEST), new FloatingCleanup.Rooms(true, false, false, false)),
                "Непустой сундук вне плана сначала переносят в сундуки двора");
    }

    @Test
    void decide_StationNeededByPlan_IsRelocated() {
        assertEquals(
                FloatingCleanup.Action.RELOCATE_STATION,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STATION), new FloatingCleanup.Rooms(true, false, false, true)),
                "Нужная плану станция не пропадает: её снимают и ставят заново по схеме двора");
    }

    @Test
    void decide_StationNotNeeded_IsRemoved() {
        assertEquals(
                FloatingCleanup.Action.REMOVE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STATION), new FloatingCleanup.Rooms(true, false, false, false)),
                "Лишняя станция вне плана снимается, чтобы не висеть в воздухе");
    }

    @Test
    void decide_UnreachableStump_IsLeftAlone() {
        assertEquals(
                FloatingCleanup.Action.SKIP_UNREACHABLE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STUMP),
                        new FloatingCleanup.Rooms(false, false, false, false, false)),
                "Висящее бревно в 12 блоках над духом недостижимо: доска не должна открывать уборку, "
                        + "иначе DISMANTLE_SHELTER бесконечно переоткрывается на том же месте");
    }

    @Test
    void decide_Stump_IsRemoved() {
        assertEquals(
                FloatingCleanup.Action.REMOVE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STUMP), new FloatingCleanup.Rooms(false, false, false, false)),
                "Пенёк рубят сразу: ему не нужен ни двор, ни реестр");
    }

    private static FloatingBlocks.Finding finding(FloatingBlocks.Kind kind) {
        return new FloatingBlocks.Finding(AT, kind, "minecraft:oak_fence", 2, "висит");
    }
}
