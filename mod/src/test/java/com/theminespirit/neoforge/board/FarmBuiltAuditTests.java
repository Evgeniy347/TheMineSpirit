package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FarmBuiltAuditTests {
    @Test
    void audit_TilledScheme_IgnoresSurroundingLeveling() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/FirstStepsRunner.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("returnfarmScheme(target)?schematicOnly(target):TicketPlans.plan(step,level,target)")
                        && compact.contains("SchematicGround.tilled(preset.schematic())")
                        && compact.contains("returnnewScriptCall(\"build_schematic\",Map.copyOf(params))"),
                "Готовность пашни проверяется по схеме, а не по выравниванию холма вокруг: иначе "
                        + "построенное поле снова получает clear_area и разрушается. При смене "
                        + "audit_TilledScheme_IgnoresSurroundingLeveling проверь FirstStepsRunner.auditPlan "
                        + "и живой wheat_field.");
    }
}
