package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ServerPublishGuardTests {
    private static final Path PUBLISH = Path.of("scripts/publish.sh");
    private static final Path NIGHTLY = Path.of("scripts/nightly-analyzers.sh");
    private static final Path CHECK = Path.of("scripts/testing/check-publish.sh");
    private static final Path PYTHON = Path.of("scripts/python-bin.sh");
    private static final Path LEAD = Path.of("docs/LEAD-AGENT.md");
    private static final Path SERVER = Path.of("server");
    private static final List<String> SERVER_SCRIPTS = List.of(
            "scripts/common.sh",
            "scripts/console.sh",
            "scripts/hang-watch.sh",
            "scripts/install-neoforge.sh",
            "scripts/start.sh",
            "scripts/stop.sh",
            "scripts/world-snapshot.sh",
            "scripts/world-restore.sh");
    private static final List<String> SERVER_FILES = List.of(
            "README.md",
            "server.properties",
            "ops.json",
            "whitelist.json",
            "banned-ips.json",
            "banned-players.json",
            "eula.txt",
            "user_jvm_args.txt",
            "run.sh");
    private static final List<String> SERVER_CONFIGS = List.of(
            "config/fml.toml",
            "config/neoforge-common.toml",
            "config/neoforge-server.toml",
            "config/theminespirit-policy.toml",
            "config/theminespirit-rest.toml",
            "config/theminespirit-server.toml",
            "config/jei/server/world/default/jei-server.ini");
    private static final Pattern OLD_SNAPSHOT = Pattern.compile("(?<!world-)snapshot\\.sh");
    private static final List<String> REQUIRED_FILES;

    static {
        List<String> all = new ArrayList<>(SERVER_SCRIPTS);
        all.addAll(SERVER_FILES);
        all.addAll(SERVER_CONFIGS);
        REQUIRED_FILES = List.copyOf(all);
    }

    @Test
    void publishScript_RejectsBrokenJar_BeforeInstall() throws IOException {
        String script = read(PUBLISH);
        assertTrue(
                script.contains("verify_mod_jar") && script.contains("META-INF/neoforge.mods.toml"),
                "publish.sh не проверяет содержимое jar: 08.11 в 0.1.3253 уехал jar с пустым "
                        + "META-INF/neoforge.mods.toml, сервер не стартовал («Missing ModLoader»), простой ~20 мин. "
                        + "Как надо: verify_mod_jar — непустой mods.toml с modId и больше 1000 классов");
        int verified = script.indexOf("jar не прошёл проверку содержимого");
        int installed = script.indexOf("echo \"Установка мода в");
        assertTrue(
                verified > 0 && installed > 0 && verified < installed,
                "Проверка jar в publish.sh должна стоять до установки (и до остановки сервера): иначе битый jar "
                        + "снова уедет в mods/ и уронит сервер");
        assertTrue(
                Files.isRegularFile(CHECK),
                "Нет scripts/testing/check-publish.sh: нечем проверить, что негодный jar не доходит до установки");
        String check = read(CHECK);
        for (String scenario : List.of("пуст (0 байт)", "не объявляет modId", "классов 1000", "--sync", "негоден")) {
            assertTrue(
                    check.contains(scenario),
                    "Проверка check-publish.sh потеряла опыт «" + scenario + "»: пустой/без modId/без классов jar, "
                            + "граница 1000 классов и --sync не должны расходиться по клиентам");
        }
        String lead = read(LEAD);
        assertTrue(
                lead.contains("neoforge.mods.toml") && lead.contains("1000"),
                "docs/LEAD-AGENT.md → «Слияние и выкладка» обязана называть, что проверяется в jar перед установкой: "
                        + "непустой neoforge.mods.toml с modId и больше 1000 классов");
    }

    @Test
    void serverFiles_LiveInModRepo_ArePresent() {
        List<String> missing = new ArrayList<>();
        for (String relative : REQUIRED_FILES) {
            Path path = SERVER.resolve(relative);
            if (!Files.isRegularFile(path) || size(path) == 0) {
                missing.add(path.toString().replace('\\', '/'));
            }
        }
        assertEquals(
                List.of(),
                missing,
                "Скрипты и конфиги сервера живут в репозитории мода (server/), а не только на диске сервера: "
                        + "иначе после удаления server/.git их история теряется. Как надо: положить файл в server/ "
                        + "(scripts/, config/, корневые конфиги). Не найдены или пусты");
    }

    @Test
    void publishScript_ServerFiles_AreDeployed() {
        String script = read(PUBLISH);
        assertTrue(
                script.contains("deploy_server_files"),
                "publish.sh обязан раскладывать server/ на сервер: иначе правка скриптов и конфигов в репозитории "
                        + "не доедет до /opt/minecraft/server. Как надо: функция deploy_server_files и её вызов "
                        + "в полной и временной публикации");
        assertTrue(
                script.contains("$SERVER_DIR/scripts"),
                "publish.sh не копирует server/scripts в $SERVER_DIR/scripts: скрипты сервера останутся старыми");
        assertTrue(
                script.contains("$SERVER_DIR/config"),
                "publish.sh не копирует server/config в $SERVER_DIR/config: конфиги сервера останутся старыми");
        assertTrue(
                script.contains("hang-watch.sh") && script.contains("crontab"),
                "publish.sh не ставит cron сторожа зависания: server/scripts/hang-watch.sh доедет до сервера, "
                        + "но не запустится по расписанию, и зависший сервер снова никто не заметит");
        assertTrue(
                script.contains("deploy_file")
                        && script.contains("deploy_tree")
                        && read(CHECK).contains("прямых cp"),
                "publish.sh раскладывает файлы сервера через deploy_file/deploy_tree, и это проверяется на "
                        + "подделке: иначе конфиги снова поедут cp -a на место, и стартующий сервер прочитает "
                        + "половину файла");
        assertFalse(
                script.contains("cp -a "),
                "publish.sh снова копирует на место (cp -a): копия обязана идти во временного соседа и подменяться "
                        + "mv, как скрипты. Прямой cp читатель видит обрезанным");
    }

    @Test
    void publishScript_NightlyAnalyzers_AreDeployedAndScheduled() throws IOException {
        String script = read(PUBLISH);
        assertTrue(
                script.contains("nightly-analyzers.sh"),
                "publish.sh обязан раскладывать scripts/nightly-analyzers.sh на сервер, как server-cleanup.sh: "
                        + "иначе ночной полный прогон анализаторов не доедет до сервера");
        assertTrue(
                script.contains("install_nightly_analyzers_cron") && script.contains("0 3 * * *"),
                "publish.sh не ставит cron ночного анализатора на 03:00: скрипт на сервере есть, но сам не "
                        + "запускается, и находки SpotBugs/PMD/Checkstyle никто не увидит");
        assertTrue(
                script.contains("BUILD_LOCK") && script.contains("acquire_build_lock"),
                "Публикация и ночная сборка должны делить один замок: иначе в 03:00 они соберут одновременно "
                        + "и отнимут процессор у живого сервера");
    }

    @Test
    void nightlyScript_FullAnalyzes_CleanTreeNoPublication() throws IOException {
        String nightly = read(NIGHTLY);
        assertTrue(
                nightly.contains("-Ptheminespirit.analyzers=on") && nightly.contains(" check"),
                "Ночной прогон обязан включать полный набор анализаторов и тесты: check с "
                        + "-Ptheminespirit.analyzers=on");
        assertTrue(
                nightly.contains("--offline"),
                "На сервере Gradle работает из офлайн-кэша (init-скрипт zz-offline-publish.gradle): без --offline "
                        + "сборка полезет в сеть и может зависнуть");
        assertTrue(
                nightly.contains("git clone") && nightly.contains("git") && nightly.contains("clean -qfdx"),
                "Ночная сборка идёт в отдельном чистом checkout: рабочие деревья сервера (the-mine-spirit) "
                        + "трогать нельзя");
        assertTrue(
                nightly.contains("nice") && nightly.contains("ionice"),
                "На машине живой сервер игры: сборка обязана идти с низким приоритетом (nice/ionice)");
        assertTrue(
                nightly.contains("flock") && nightly.contains("NIGHTLY_STATE"),
                "Нужны замок против одновременной публикации/сборки и sha последнего проверенного коммита: "
                        + "без них ночной прогон повторяет одну и ту же сборку");
        assertTrue(
                nightly.contains("NIGHTLY_STATUS") && nightly.contains("NIGHTLY_LOG"),
                "Результат обязан попадать в краткий лог и статус-файл: иначе находки анализаторов никто не найдёт");
        String code = nightly.replaceAll("(?m)^\\s*#.*$", "");
        assertFalse(
                code.contains("publish.sh") || code.contains("world-snapshot.sh") || code.contains("stop.sh"),
                "Ночной анализатор не публикует мод и не перезапускает сервер: только сборка и отчёт");
        assertFalse(
                code.contains("python_bin") || code.contains("python3") || code.contains("count_xml"),
                "Сводка находок ночного прогона снова зависит от python (и от grep-запасного пути без имён "
                        + "файлов): на машине без python находки теряются, а разбор отчётов расходится на две "
                        + "реализации. Как надо: один разбор XML на java из toolchain Gradle, без запасного пути");
        assertTrue(
                code.contains("AnalyzerSummary") && code.contains("build/reports/"),
                "Ночной прогон обязан строить сводку находок сам (AnalyzerSummary по отчётам build/reports/…): "
                        + "иначе SpotBugs/PMD/Checkstyle/TESTS остаются без имён файлов или без сводки вовсе");
    }

    @Test
    void publishScripts_PythonSelection_IsOneHelper() throws IOException {
        assertTrue(
                Files.isRegularFile(PYTHON),
                "Нет scripts/python-bin.sh: выбор python (THEMINESPIRIT_PYTHON → PYTHON → python3 → python) "
                        + "скопирован в publish.sh и check-publish.sh, и две копии расходятся: на сервере один "
                        + "скрипт находит python, другой нет");
        String helper = read(PYTHON);
        assertTrue(
                helper.contains("python_bin") && helper.contains("THEMINESPIRIT_PYTHON"),
                "scripts/python-bin.sh обязан объявлять python_bin и порядок THEMINESPIRIT_PYTHON → PYTHON → "
                        + "python3 → python: один выбор python на все скрипты");
        for (Path script : List.of(PUBLISH, CHECK)) {
            String text = read(script);
            assertTrue(
                    text.contains("python-bin.sh"),
                    script + " не берёт общий выбор python из scripts/python-bin.sh: копия выбора снова "
                            + "разойдётся с остальными, и на сервере скрипт молча не найдёт python");
            assertFalse(
                    text.contains("for candidate in") || text.contains("for path in \"${THEMINESPIRIT_PYTHON"),
                    script + " всё ещё перебирает python сам, а не зовёт python_bin из scripts/python-bin.sh: "
                            + "правка порядка поиска должна быть одна на все скрипты");
        }
    }

    @Test
    void publishScript_Snapshot_UsesArchiveWithPublishLabel() {
        String script = read(PUBLISH);
        assertTrue(
                script.contains("world-snapshot.sh"),
                "Снимок мира перед публикацией должен идти через world-snapshot.sh (tar-архив), а не через git");
        assertTrue(
                script.contains("snapshot_label=\"publish-$version\""),
                "Публикационный снимок обязан иметь метку publish-<версия>: так его видно в backups/world и "
                        + "ротация его не удаляет. Как надо: snapshot_label=\"publish-$version\"");
        String withoutRemoval =
                script.replaceAll("(?m)^\\s*rm -f \\\"\\$SERVER_DIR/scripts/snapshot\\.sh\\\"\\s*$", "");
        assertFalse(
                OLD_SNAPSHOT.matcher(withoutRemoval).find(),
                "publish.sh всё ещё зовёт snapshot.sh (git-снимок): мир в git больше не храним");
        assertFalse(
                Files.exists(Path.of("scripts/snapshot.sh")),
                "scripts/snapshot.sh (git-снимок мира) должен быть удалён: мир в git больше не храним");
        assertFalse(
                Files.exists(SERVER.resolve("scripts/snapshot.sh")),
                "server/scripts/snapshot.sh (git-снимок мира) должен быть удалён: мир в git больше не храним");
    }

    @Test
    void serverGit_Removed_IsNotReferenced() {
        List<String> offenders = new ArrayList<>();
        for (String text : instructionTexts()) {
            if (text.contains("git/server.git") || text.contains("server/.git")) {
                offenders.add(text.substring(0, Math.min(text.length(), 80)));
            }
        }
        assertEquals(
                List.of(),
                offenders,
                "Упоминания git-репозитория мира (server/.git, /opt/minecraft/git/server.git) остались в инструкциях "
                        + "или скриптах: читатель пойдёт коммитить мир в удалённый репозиторий. Как надо: снимки — "
                        + "world-snapshot.sh, история скриптов и конфигов — в репозитории мода (server/)");
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать размер " + path, e);
        }
    }

    private static List<String> instructionTexts() {
        List<Path> roots =
                List.of(Path.of("AGENTS.md"), Path.of("README.md"), Path.of("docs"), Path.of("scripts"), SERVER);
        List<String> texts = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isRegularFile(root)) {
                texts.add(read(root));
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".md")
                                || path.toString().endsWith(".sh"))
                        .toList()) {
                    texts.add(read(file));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Сторож не смог прочитать " + root, e);
            }
        }
        return texts;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + path, e);
        }
    }
}
