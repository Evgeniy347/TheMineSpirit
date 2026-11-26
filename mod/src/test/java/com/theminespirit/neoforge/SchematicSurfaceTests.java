package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchematicSurfaceTests {
    @Test
    void tick_BelowSurface_DelegatesBeforePlanning() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicWorkPhase.java"));
        String compact = source.replaceAll("\\s+", "");
        int recovery = compact.indexOf("Optional<ScriptStatus>recovered=recovery.tick();");
        int returned = compact.indexOf("returnrecovered.orElseThrow();", recovery);
        int planning = compact.indexOf("host.ticks++;", recovery);

        assertTrue(
                recovery >= 0 && returned > recovery && planning > returned,
                "Пока дух ниже пашни, основной автомат отдаёт тик восстановлению поверхности: иначе стройка "
                        + "снова начинается внутри farmland. При смене tick_BelowSurface_DelegatesBeforePlanning "
                        + "проверь SchematicWorkPhase.tick и живой wheat_field.");
    }

    @Test
    void recovery_ActiveSurfaceApproach_TicksInsteadOfRestarting() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicSurfaceRecovery.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("if(stand==null||!host.approach.isActive())")
                        && compact.contains("returnOptional.of(approachTick())")
                        && compact.contains("caseARRIVED->"),
                "Начатый выход к поверхности продолжает тикать, а не перезапускается каждый тик: иначе дух "
                        + "никогда не выйдет из ямы. При смене recovery_ActiveSurfaceApproach_TicksInsteadOfRestarting "
                        + "проверь SchematicSurfaceRecovery.approachTick.");
    }

    @Test
    void recovery_ClearVerticalColumn_LeavesPitWithoutPath() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicSurfaceRecovery.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("verticalClear(surface)")
                        && compact.contains("DigOutSpace.passable(")
                        && compact.contains("DigOutStep.moveTo("),
                "Свободная колонна над ямой позволяет двигать духа к поверхности без поиска пути: иначе "
                        + "аварийный подход зависает и стройка не начинается. При смене "
                        + "recovery_ClearVerticalColumn_LeavesPitWithoutPath проверь SchematicSurfaceRecovery "
                        + "и живой wheat_field.");
    }

    @Test
    void base_BeforePlacement_PreparesEarthUnderGroundCells() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicWorkPhase.java"));
        String compact = source.replaceAll("\\s+", "");
        int clear = compact.indexOf("returnclearStep(clear);");
        int base = compact.indexOf("host.fieldBase.tick()", clear);
        int placement = compact.indexOf("host.planPhase.startPlan()", base);

        assertTrue(
                clear >= 0 && base > clear && placement > base,
                "После расчистки и до любой постановки проверяется основание поля: иначе вода оказывается "
                        + "над камнем или воздухом. При смене base_BeforePlacement_PreparesEarthUnderGroundCells "
                        + "проверь SchematicWorkPhase.planTick и живое поле.");
    }

    @Test
    void base_NonEarthBelow_ReplacesWithDirt() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicFieldBase.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("BlockPosbelow=target.below();")
                        && compact.contains("countItem(Items.DIRT)")
                        && compact.contains("SpiritBlocks.destroy(server,below,true,spirit,\"FIELD_BASE\")")
                        && compact.contains(
                                "SpiritBlocks.place(server,below,Blocks.DIRT.defaultBlockState(),spirit,\"FIELD_BASE\")"),
                "Под пашней, тропой и водой стоит земля: иначе вода выливается ниже грядки. При смене "
                        + "base_NonEarthBelow_ReplacesWithDirt проверь SchematicFieldBase.prepare и живой y-1.");
    }

    @Test
    void supply_BaseEarth_IsFetchedAndKept() throws IOException {
        String need = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/BuildSupplyNeed.java"));
        String trip = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/BuildSupplyTrip.java"));

        assertTrue(
                need.contains("SchematicFieldBase.missingBaseCount(site)")
                        && trip.contains("needed.add(\"minecraft:dirt\")"),
                "Земля основания входит в подвоз и не выбрасывается как лишняя: иначе дух снова встанет без "
                        + "материала под уже готовой пашней. При смене supply_BaseEarth_IsFetchedAndKept "
                        + "проверь BuildSupplyNeed и BuildSupplyTrip.");
    }

    @Test
    void clearance_StrayWater_InSeedCellIsRemoved() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicClearance.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("(current.canBeReplaced()&&current.getFluidState().isEmpty())")
                        && compact.contains("SchematicActions.isFluidPlaced(entry.block())")
                        && compact.contains("current.getFluidState().equals(site.wanted(entry).getFluidState())"),
                "Вода в клетке посева удаляется до семян, а запланированный источник воды сохраняется: иначе "
                        + "семена не встанут или построенный источник будут сносить. При смене "
                        + "clearance_StrayWater_InSeedCellIsRemoved проверь SchematicClearance.toClear.");
    }

    @Test
    void recovery_NoSafeSurface_FailsWithClearReason() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicSurfaceRecovery.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("cell.y()>=host.site.origin().y()&&host.site.standable(cell)")
                        && compact.contains("surface.y()<host.site.origin().y()||!host.site.standable(surface)")
                        && compact.contains("нетбезопаснойстоянкинаповерхностипашни"),
                "Нет безопасной поверхности — понятный провал, а не расхождение со схемой: иначе оператор "
                        + "снова видит 31 недостижимую клетку без причины. При смене "
                        + "recovery_NoSafeSurface_FailsWithClearReason проверь фильтр StandSearch и живое поле.");
    }
}
