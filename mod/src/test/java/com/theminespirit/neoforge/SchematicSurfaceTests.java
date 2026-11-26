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
