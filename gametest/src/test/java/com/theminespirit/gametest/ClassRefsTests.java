package com.theminespirit.gametest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClassRefsTests {
    @Test
    void of_ClassLiteral_IsAReference() {
        assertTrue(
                ClassRefs.of(Sample.class).contains(Sample.owner().getName().replace('.', '/')),
                "Ссылка сцены на класс шагов — это литерал класса в байтах: без него фильтр по классу из одних "
                        + "@SceneStep не находит сцену, которая эти шаги гоняет");
    }

    @Test
    void of_ClassNameInString_IsNotAReference() {
        assertFalse(
                ClassRefs.of(TextOnly.class).contains(TextOnly.name()),
                "Имя класса в строке — не вызов: сцена, которая только печатает имя, не должна попадать под "
                        + "фильтр чужого класса шагов");
    }

    @Test
    void parse_NotAClassFile_FailsLoudly() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ClassRefs.parse(new byte[] {1, 2, 3, 4, 5}),
                "Мусор вместо байт класса обязан падать: иначе фильтр молча не увидит ни одной ссылки");

        assertTrue(
                error.getMessage().contains("0xCAFEBABE"),
                "Ошибка обязана называть причину, иначе непонятно, что читать: " + error.getMessage());
    }

    private static final class Sample {
        private static final Class<?> OWNER = ClassRefsTests.class;

        private static Class<?> owner() {
            return OWNER;
        }
    }

    private static final class TextOnly {
        private static final String NAME = "com/theminespirit/gametest/DigOutGameTests";

        private static String name() {
            return NAME;
        }
    }
}
