package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShelterSchematicTests {
    private static final Path FILE = Path.of("mod/src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT + "shelter.txt");

    private static SchematicDocument document() throws IOException {
        return SchematicTextParser.parse(Files.readString(FILE), FILE.toString());
    }

    @Test
    void shelter_BodyAtLeastSixBySix_NoPrisonBox() throws IOException {
        SchematicDocument document = document();
        Schematic schematic = document.schematic();
        BlockCoord entry = document.header().entrance().orElseThrow();
        SchematicBody body = SchematicBody.of(schematic, entry);

        assertTrue(
                body.sizeX() >= 6 && body.sizeZ() >= 6,
                "Укрытие обязано быть не меньше 6×6 снаружи, а тело " + body.sizeX() + "×" + body.sizeZ()
                        + ": коробка 3×3 — хуже тюрьмы (владелец 21.10)"
                        + " Иначе тест shelter_BodyAtLeastSixBySix_NoPrisonBox перестанет ловить эту поломку. При смене shelter_BodyAtLeastSixBySix_NoPrisonBox проверь это утверждение.");
    }

    @Test
    void shelter_InnerCeiling_ThreeBlocksAboveFloor() throws IOException {
        Map<BlockCoord, Schematic.Entry> grid = new HashMap<>();
        for (Schematic.Entry entry : document().schematic().blocks()) {
            grid.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry);
        }

        assertEquals(
                "cobblestone",
                SchematicBlocks.path(grid.get(new BlockCoord(2, 0, 4)).block()),
                "У укрытия обязан быть пол: без него не мерить высоту"
                        + " Иначе тест shelter_InnerCeiling_ThreeBlocksAboveFloor перестанет ловить эту поломку. При смене shelter_InnerCeiling_ThreeBlocksAboveFloor проверь это утверждение.");
        for (int y = 1; y <= 3; y++) {
            assertFalse(
                    grid.containsKey(new BlockCoord(2, y, 4)),
                    "Внутри на y=" + y + " не должно быть блока: потолок держится на высоте 3"
                            + " Иначе тест shelter_InnerCeiling_ThreeBlocksAboveFloor перестанет ловить эту поломку. При смене shelter_InnerCeiling_ThreeBlocksAboveFloor проверь это утверждение.");
        }
        assertEquals(
                "cobblestone",
                SchematicBlocks.path(grid.get(new BlockCoord(2, 4, 4)).block()),
                "Потолок обязан быть на высоте 3 над полом"
                        + " Иначе тест shelter_InnerCeiling_ThreeBlocksAboveFloor перестанет ловить эту поломку. При смене shelter_InnerCeiling_ThreeBlocksAboveFloor проверь это утверждение.");
    }

    @Test
    void shelter_Entrance_IsOpenAndLit() throws IOException {
        SchematicDocument document = document();
        BlockCoord entry = document.header().entrance().orElseThrow();
        Map<BlockCoord, Schematic.Entry> grid = new HashMap<>();
        for (Schematic.Entry block : document.schematic().blocks()) {
            grid.put(new BlockCoord(block.dx(), block.dy(), block.dz()), block);
        }

        assertFalse(
                grid.containsKey(entry),
                "Проём входа обязан быть свободен: иначе строитель замурован (INC-10)"
                        + " При смене shelter_Entrance_IsOpenAndLit проверь это утверждение.");
        assertFalse(
                grid.containsKey(new BlockCoord(entry.x(), entry.y() + 1, entry.z())),
                "Проём входа обязан быть 1×2: верхняя клетка тоже свободна"
                        + " Иначе тест shelter_Entrance_IsOpenAndLit перестанет ловить эту поломку. При смене shelter_Entrance_IsOpenAndLit проверь это утверждение.");
        assertTrue(
                SchematicLight.hasSource(document.schematic()),
                "В укрытии обязан быть источник света — факел: ночью внутри появляются мобы"
                        + " Иначе тест shelter_Entrance_IsOpenAndLit перестанет ловить эту поломку. При смене shelter_Entrance_IsOpenAndLit проверь это утверждение.");
        boolean torch = document.schematic().blocks().stream()
                .anyMatch(block -> "torch".equals(SchematicBlocks.path(block.block())));
        assertTrue(
                torch,
                "Свет укрытия — именно факел: его наличие проверяет build_shelter до первого блока"
                        + " Иначе тест shelter_Entrance_IsOpenAndLit перестанет ловить эту поломку. При смене shelter_Entrance_IsOpenAndLit проверь это утверждение.");
    }

    @Test
    void shelter_SchematicCheck_AllRulesPass() throws IOException {
        SchematicCheck.Report report = SchematicCheck.check("shelter", document());

        assertEquals(
                List.of(),
                report.problems(),
                "Укрытие обязано проходить все сторожа SchematicRules без исключений: стены, вход, крыша, высота, свет, "
                        + "опора. Нарушения"
                        + " Иначе тест shelter_SchematicCheck_AllRulesPass перестанет ловить эту поломку. При смене shelter_SchematicCheck_AllRulesPass проверь это утверждение.");
    }
}
