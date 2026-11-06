package com.theminespirit.guards;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.guards.archfixture.HttpClientUser;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTests {
    private static final String[] GAME_PACKAGES = {"net.minecraft..", "net.neoforged..", "com.mojang.."};
    private static final String[] HTTP_PACKAGES = {"com.sun.net.httpserver..", "java.net.http.."};
    private static final String[] CLIENT_PACKAGES = {
        "net.minecraft.client..", "net.neoforged.neoforge.client..", "com.mojang.blaze3d.."
    };

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.theminespirit");

    @Test
    void core_Dependencies_NothingFromMinecraftOrNeoForge() {
        assertTrue(
                PRODUCTION.contain("com.theminespirit.core.observation.BlockCoord"),
                "Сторож не нашёл классы core: переименование пакета молча делает сторож зелёным на нуле классов");
        forbid(
                        "com.theminespirit.core..",
                        GAME_PACKAGES,
                        "пакет core — быстрый путь: его тестирует чистый JUnit без запуска игры, и classpath тестов "
                                + "не содержит Minecraft/NeoForge; код, которому нужна игра, переносится в другой "
                                + "пакет и проверяется GameTest (docs/TESTING.md)")
                .check(PRODUCTION);
    }

    @Test
    void restCore_Dependencies_NoJdkHttpTypes() {
        forbid(
                        "com.theminespirit.core.rest..",
                        HTTP_PACKAGES,
                        "core/rest — маршрутизация, валидация и сериализация без транспорта: типы HTTP живут только "
                                + "в neoforge/rest (RestServer), иначе замена HTTP-слоя или тест без сокетов "
                                + "невозможны; в core остаются RestRequest/RestResponse")
                .check(PRODUCTION);
    }

    @Test
    void serverCode_Dependencies_NoClientOnlyClasses() {
        noClasses()
                .that()
                .resideInAPackage("com.theminespirit..")
                .and()
                .resideOutsideOfPackage("com.theminespirit.neoforge.client..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(CLIENT_PACKAGES)
                .because("классов net.minecraft.client на выделенном сервере нет: класс, ссылающийся на них, "
                        + "роняет сервер при загрузке; клиентский код живёт только в neoforge/client")
                .check(PRODUCTION);
    }

    @Test
    void dependencyRule_ForbiddenImport_IsReported() {
        JavaClasses fixture = new ClassFileImporter().importClasses(HttpClientUser.class);

        EvaluationResult result = forbid("com.theminespirit.guards.archfixture..", HTTP_PACKAGES, "проверка сторожа")
                .evaluate(fixture);

        assertTrue(
                result.hasViolation() && result.getFailureReport().toString().contains("java.net.http.HttpClient"),
                "Сторож ослеп: класс с зависимостью от java.net.http обязан попасть в нарушители: "
                        + result.getFailureReport());
    }

    private static ArchRule forbid(String from, String[] forbidden, String reason) {
        return noClasses()
                .that()
                .resideInAPackage(from)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(forbidden)
                .because(reason);
    }
}
