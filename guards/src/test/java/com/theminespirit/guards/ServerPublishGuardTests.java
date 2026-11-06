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
