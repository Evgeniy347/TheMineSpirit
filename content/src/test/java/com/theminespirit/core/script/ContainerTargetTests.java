package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContainerTargetTests {
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "nearest|NEAREST",
                "  nearest  |NEAREST",
                "auto|AUTO",
                "  AUTO  |AUTO",
                "12,64,-7|POSITION",
                "0,0,0|POSITION",
                "-30000000,319,30000000|POSITION",
                "1,2,3|POSITION"
            })
    void parse_TargetText_GivesExpectedKind(String text, String expected) {
        ContainerTarget target = ContainerTarget.parse(text);

        boolean matches = switch (expected) {
            case "NEAREST" -> target instanceof ContainerTarget.Nearest;
            case "AUTO" -> target instanceof ContainerTarget.Auto;
            default -> target instanceof ContainerTarget.Position;
        };
        assertEquals(
                true,
                matches,
                "Цель '" + text + "' должна разобраться в " + expected + " иначе скрипт контейнера не "
                        + "поймёт, куда идти"
                        + " При смене parse_TargetText_GivesExpectedKind проверь это утверждение.");
    }

    @Test
    void parse_Auto_ReturnsSharedInstance() {
        assertEquals(
                ContainerTarget.AUTO,
                ContainerTarget.parse("auto"),
                "'auto' всегда даёт одно и то же значение: по нему unload раскладывает предметы по своему "
                        + "выбору, отдельных экземпляров быть не должно"
                        + " Иначе тест parse_Auto_ReturnsSharedInstance перестанет ловить эту поломку. При смене parse_Auto_ReturnsSharedInstance проверь это утверждение.");
    }

    @Test
    void parse_Coordinates_GivesPositionOfBlock() {
        ContainerTarget target = ContainerTarget.parse("12,64,-7");

        assertEquals(
                new ContainerTarget.Position(12, 64, -7),
                target,
                "Координаты контейнера переносятся в цель без потерь: сдвиг на блок и дух открывает "
                        + "чужой сундук или упирается в стену"
                        + " Иначе тест parse_Coordinates_GivesPositionOfBlock перестанет ловить эту поломку. При смене parse_Coordinates_GivesPositionOfBlock проверь это утверждение.");
    }

    @Test
    void parse_Nearest_ReturnsSharedInstance() {
        assertEquals(
                ContainerTarget.NEAREST,
                ContainerTarget.parse("nearest"),
                "'nearest' всегда даёт одно и то же значение: сравнивать цель с NEAREST должно быть "
                        + "достаточно, отдельных экземпляров быть не должно"
                        + " Иначе тест parse_Nearest_ReturnsSharedInstance перестанет ловить эту поломку. При смене parse_Nearest_ReturnsSharedInstance проверь это утверждение.");
    }

    @ParameterizedTest(name = "«{0}» — отказ")
    @CsvSource(
            delimiter = '|',
            value = {
                "a,b,c",
                "1,2",
                "1,2,3,4",
                "0,-65,0",
                "0,320,0",
                "30000001,0,0",
                "-30000001,0,0",
                "12.5,64,-7",
                "12,64,-7,",
                "12, sixty-four,-7",
                "nearest, 1,2"
            })
    void parse_BrokenTarget_ThrowsWithFormatExample(String text) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ContainerTarget.parse(text),
                "Цель '" + text + "' обязана быть отказом: молчаливое «ближайший» вместо опечатки "
                        + "отправило бы духа к чужому сундуку"
                        + " Иначе тест parse_BrokenTarget_ThrowsWithFormatExample перестанет ловить эту поломку. При смене parse_BrokenTarget_ThrowsWithFormatExample проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(text.trim()),
                "Сообщение об ошибке обязано называть плохую цель '" + text + "': по ней автор плана "
                        + "находит опечатку. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_BrokenTarget_ThrowsWithFormatExample перестанет ловить эту поломку. При смене parse_BrokenTarget_ThrowsWithFormatExample проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("x,y,z"),
                "Сообщение об ошибке обязано показывать формат 'x,y,z': без примера формата автор плана "
                        + "не поймёт, что писать. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_BrokenTarget_ThrowsWithFormatExample перестанет ловить эту поломку. При смене parse_BrokenTarget_ThrowsWithFormatExample проверь это утверждение.");
    }

    @Test
    void parse_WorldBoundaryCoordinates_AreAccepted() {
        assertEquals(
                new ContainerTarget.Position(30_000_000, 319, -30_000_000),
                ContainerTarget.parse("30000000,319,-30000000"),
                "Границы мира принимаются: блок на границе существует, и отказ по нему сломал бы "
                        + "склад на краю карты"
                        + " Иначе тест parse_WorldBoundaryCoordinates_AreAccepted перестанет ловить эту поломку. При смене parse_WorldBoundaryCoordinates_AreAccepted проверь это утверждение.");
    }
}
