package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SpiritCommandDocGuardTests {
    private static final Path README = Path.of("README.md");
    private static final Path NEOFORGE = Path.of("src/main/java/com/theminespirit/neoforge");
    private static final String COMMANDS_HEADING = "## Команды духов";
    private static final Pattern REGISTERED = Pattern.compile("spirit\\.then\\(literal\\(\"([a-z_]+)\"\\)");
    private static final Pattern DOCUMENTED = Pattern.compile("`/spirit ([a-z_]+)");

    @Test
    void subcommand_RegisteredButNotDocumented_IsReported() throws IOException {
        List<String> missing = missingCommands(registeredCommands(), commandsSection(readReadme()));

        assertEquals(
                List.of(),
                missing,
                "Подкоманда /spirit зарегистрирована в neoforge (SpiritCommands → Spirit*Command), но не описана "
                        + "в README.md → «Команды духов»: игрок и ИИ-агент не узнают, что команда существует, "
                        + "а карта команд расходится с кодом. Допишите строку вида «/spirit <имя> <аргументы>» "
                        + "в таблицу раздела. Правило: подкоманды /spirit и README.md → «Команды духов» не расходятся. "
                        + "Не описаны");
    }

    @Test
    void documentedCommand_NotRegistered_IsReported() throws IOException {
        List<String> unknown = documentedCommands(commandsSection(readReadme()));
        unknown.removeAll(registeredCommands());

        assertEquals(
                List.of(),
                unknown,
                "В README.md → «Команды духов» описана подкоманда /spirit, которой нет среди зарегистрированных: "
                        + "игрок наберёт её и получит ошибку, а документ обещает то, чего мод не умеет. "
                        + "Уберите устаревшую строку или зарегистрируйте команду в SpiritCommands.register. "
                        + "Правило: README.md → «Команды духов» и код не расходятся. Лишние упоминания");
    }

    @Test
    void missingCommands_RegisteredCommandAbsentFromDoc_IsReported() {
        assertEquals(
                List.of("observe"),
                missingCommands(Set.of("spawn", "observe"), "тут `/spirit spawn [pos]`"),
                "Сторож ослеп: зарегистрированная подкоманда, которой нет в разделе команд, обязана попасть в пропажу");
    }

    @Test
    void missingCommands_AllDocumented_ReportsNothing() {
        assertEquals(
                List.of(),
                missingCommands(Set.of("spawn", "observe"), "тут `/spirit spawn` и `/spirit observe`"),
                "Сторож ложно срабатывает: упомянутые в разделе подкоманды — не пропажа");
    }

    @Test
    void registeredCommands_RegistrationLine_IsFound() {
        assertEquals(
                List.of("spawn"),
                registeredCommands("spirit.then(literal(\"spawn\").executes(context -> 0));"),
                "Сторож ослеп: строка регистрации подкоманды spirit.then(literal(\"…\")) обязана находиться");
    }

    @Test
    void registeredCommands_NestedLiteral_NotReported() {
        assertEquals(
                List.of(),
                registeredCommands(".then(literal(\"clear\").executes(context -> 0))"),
                "Сторож ложно срабатывает: вложенный литерал (аргумент подкоманды) — не подкоманда /spirit");
    }

    @Test
    void documentedCommands_CommandInBackticks_IsFound() {
        assertEquals(
                List.of("spawn"),
                documentedCommands("`/spirit spawn [pos]`"),
                "Сторож ослеп: подкоманда, записанная обратными кавычками, обязана находиться");
    }

    @Test
    void documentedCommands_TextOutsideBackticks_NotReported() {
        assertEquals(
                List.of(),
                documentedCommands("команда /spirit spawn без кавычек"),
                "Сторож ложно срабатывает: имя подкоманды вне обратных кавычек — не упоминание команды");
    }

    @Test
    void commandsSection_Readme_IsFound() {
        String section = commandsSection("## Команды духов\nПодкоманды `/spirit spawn`.\n## Конфиг\n");

        assertTrue(
                section.contains("/spirit spawn"),
                "Сторож ослеп: раздел команд обязан находиться по заголовку и содержать текст до следующего заголовка");
        assertTrue(
                !section.contains("## Конфиг"),
                "Сторож слеп к границе: раздел команд обязан кончаться на следующем заголовке `## `");
    }

    private static String readReadme() throws IOException {
        assertTrue(
                Files.isRegularFile(README),
                "Сторож не нашёл " + README + ": тесты запускаются из корня проекта (./gradlew test)");
        return Files.readString(README, StandardCharsets.UTF_8);
    }

    private static Set<String> registeredCommands() {
        Set<String> names = new TreeSet<>();
        for (String source : SourceFiles.javaUnder(NEOFORGE).values()) {
            names.addAll(registeredCommands(source));
        }
        return names;
    }

    private static List<String> registeredCommands(String source) {
        List<String> names = new ArrayList<>();
        Matcher matcher = REGISTERED.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(new TreeSet<>(names));
    }

    private static List<String> documentedCommands(String text) {
        List<String> names = new ArrayList<>();
        Matcher matcher = DOCUMENTED.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(new TreeSet<>(names));
    }

    private static List<String> missingCommands(Set<String> registered, String section) {
        Set<String> documented = new TreeSet<>(documentedCommands(section));
        List<String> missing = new ArrayList<>();
        for (String name : new TreeSet<>(registered)) {
            if (!documented.contains(name)) {
                missing.add(name);
            }
        }
        return missing;
    }

    private static String commandsSection(String readme) {
        int start = readme.indexOf(COMMANDS_HEADING);
        assertTrue(
                start >= 0,
                "В README.md нет раздела «" + COMMANDS_HEADING + "»: заголовок переименован — поправь сторожа");
        int next = readme.indexOf("\n## ", start + COMMANDS_HEADING.length());
        return next < 0 ? readme.substring(start) : readme.substring(start, next);
    }
}
