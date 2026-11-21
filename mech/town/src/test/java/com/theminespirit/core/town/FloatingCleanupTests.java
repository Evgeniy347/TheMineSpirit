package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class FloatingCleanupTests {
    private static final BlockCoord AT = new BlockCoord(1, 70, 1);

    @Test
    void decide_FloatingTreeOnPlayerPlot_IsRemoved() {
        assertEquals(
                FloatingCleanup.Action.REMOVE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.TREE), new FloatingCleanup.Rooms(true, true, true, false)),
                "Низ висящего дерева часто помечен игроком (там рос саженец), но крона без опоры — не постройка: "
                        + "её рубят, иначе висящие брёвна остаются навсегда"
                        + " При смене decide_FloatingTreeOnPlayerPlot_IsRemoved проверь это утверждение.");
    }

    @Test
    void decide_FloatingTreeWithoutStand_IsSkipped() {
        assertEquals(
                FloatingCleanup.Action.SKIP_UNREACHABLE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.TREE), new FloatingCleanup.Rooms(true, false, true, false, false)),
                "К висящему дереву без стоянки у земли не подойти: задача не выдаётся, пока нет подхода"
                        + " Иначе тест decide_FloatingTreeWithoutStand_IsSkipped перестанет ловить эту поломку. При смене decide_FloatingTreeWithoutStand_IsSkipped проверь это утверждение.");
    }

    @Test
    void decide_PlayerBuilding_IsProtected() {
        assertEquals(
                FloatingCleanup.Action.SKIP_PROTECTED,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.FENCE), new FloatingCleanup.Rooms(true, true, true, false)),
                "Постройку игрока уборка не трогает даже с готовым двором: защита старше порядка"
                        + " Иначе тест decide_PlayerBuilding_IsProtected перестанет ловить эту поломку. При смене decide_PlayerBuilding_IsProtected проверь это утверждение.");
    }

    @Test
    void decide_RegisteredChest_WaitsForRegistry() {
        assertEquals(
                FloatingCleanup.Action.SKIP_REGISTERED,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.REGISTERED_CHEST),
                        new FloatingCleanup.Rooms(true, false, false, false)),
                "Сундук из реестра сначала освобождает OldChests: уборка не снимает его с содержимым"
                        + " Иначе тест decide_RegisteredChest_WaitsForRegistry перестанет ловить эту поломку. При смене decide_RegisteredChest_WaitsForRegistry проверь это утверждение.");
    }

    @Test
    void decide_EmptyChestWithoutYard_IsLeft() {
        assertEquals(
                FloatingCleanup.Action.SKIP_WAIT,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.CHEST), new FloatingCleanup.Rooms(false, false, true, false)),
                "Без сундуков двора переносить некуда: уборка ждёт, а не теряет вещи"
                        + " Иначе тест decide_EmptyChestWithoutYard_IsLeft перестанет ловить эту поломку. При смене decide_EmptyChestWithoutYard_IsLeft проверь это утверждение.");
    }

    @Test
    void decide_EmptyChestWithYard_IsRemoved() {
        assertEquals(
                FloatingCleanup.Action.REMOVE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.CHEST), new FloatingCleanup.Rooms(true, false, true, false)),
                "Пустой сундук вне плана снимают: содержимого, которое надо спасать, нет"
                        + " Иначе тест decide_EmptyChestWithYard_IsRemoved перестанет ловить эту поломку. При смене decide_EmptyChestWithYard_IsRemoved проверь это утверждение.");
    }

    @Test
    void decide_FullChestWithYard_IsDrained() {
        assertEquals(
                FloatingCleanup.Action.DRAIN_CHEST,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.CHEST), new FloatingCleanup.Rooms(true, false, false, false)),
                "Непустой сундук вне плана сначала переносят в сундуки двора"
                        + " Иначе тест decide_FullChestWithYard_IsDrained перестанет ловить эту поломку. При смене decide_FullChestWithYard_IsDrained проверь это утверждение.");
    }

    @Test
    void decide_StationNeededByPlan_IsRelocated() {
        assertEquals(
                FloatingCleanup.Action.RELOCATE_STATION,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STATION), new FloatingCleanup.Rooms(true, false, false, true)),
                "Нужная плану станция не пропадает: её снимают и ставят заново по схеме двора"
                        + " Иначе тест decide_StationNeededByPlan_IsRelocated перестанет ловить эту поломку. При смене decide_StationNeededByPlan_IsRelocated проверь это утверждение.");
    }

    @Test
    void decide_StationNotNeeded_IsRemoved() {
        assertEquals(
                FloatingCleanup.Action.REMOVE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STATION), new FloatingCleanup.Rooms(true, false, false, false)),
                "Лишняя станция вне плана снимается, чтобы не висеть в воздухе"
                        + " Иначе тест decide_StationNotNeeded_IsRemoved перестанет ловить эту поломку. При смене decide_StationNotNeeded_IsRemoved проверь это утверждение.");
    }

    @Test
    void decide_ProtectedStation_IsSkippedInsteadOfRelocated() {
        assertEquals(
                FloatingCleanup.Action.SKIP_PROTECTED,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STATION), new FloatingCleanup.Rooms(true, true, false, true)),
                "Станция внутри готовой постройки не ломается и не переносится: она часть мира (observe14: печь "
                        + "−169,71,−489), а не висящий остаток; иначе уборка вечно падает на PROTECTED_BLOCKS"
                        + " При смене decide_ProtectedStation_IsSkippedInsteadOfRelocated проверь это утверждение.");
    }

    @Test
    void decide_UnreachableStump_IsLeftAlone() {
        assertEquals(
                FloatingCleanup.Action.SKIP_UNREACHABLE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STUMP),
                        new FloatingCleanup.Rooms(false, false, false, false, false)),
                "Висящее бревно в 12 блоках над духом недостижимо: доска не должна открывать уборку, "
                        + "иначе DISMANTLE_SHELTER бесконечно переоткрывается на том же месте"
                        + " При смене decide_UnreachableStump_IsLeftAlone проверь это утверждение.");
    }

    @Test
    void decide_PlannedFence_IsLeftAlone() {
        assertEquals(
                FloatingCleanup.Action.SKIP_PLANNED,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.FENCE),
                        new FloatingCleanup.Rooms(true, false, false, false, true, true)),
                "Забор, совпадающий с планом ограды, уборка не трогает: иначе она снимает уже построенную "
                        + "оградную линию и задача строится заново"
                        + " При смене decide_PlannedFence_IsLeftAlone проверь это утверждение.");
    }

    @Test
    void decide_Stump_IsRemoved() {
        assertEquals(
                FloatingCleanup.Action.REMOVE,
                FloatingCleanup.decide(
                        finding(FloatingBlocks.Kind.STUMP), new FloatingCleanup.Rooms(false, false, false, false)),
                "Пенёк рубят сразу: ему не нужен ни двор, ни реестр"
                        + " Иначе тест decide_Stump_IsRemoved перестанет ловить эту поломку. При смене decide_Stump_IsRemoved проверь это утверждение.");
    }

    private static FloatingBlocks.Finding finding(FloatingBlocks.Kind kind) {
        return new FloatingBlocks.Finding(AT, kind, "minecraft:oak_fence", 2, "висит");
    }
}
