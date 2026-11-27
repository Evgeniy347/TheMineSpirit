package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldSnapshotGuardTests {
    private static final Path SNAPSHOT = Path.of("server/scripts/world-snapshot.sh");

    private static final Path PUBLISH = Path.of("scripts/publish.sh");

    @Test
    void snapshot_LabeledArchive_UsesGlobWithoutHead() throws IOException {
        String source = Files.readString(PUBLISH);
        int start = source.indexOf("snapshot_world()");
        int end = source.indexOf("\nupdate_page()", start);
        String snapshot = source.substring(start, end);

        assertFalse(
                snapshot.contains("| head -n 1"),
                "Поиск архива публикации не использует ls|head: иначе pipefail превращает длинный список "
                        + "в SIGPIPE после успешного снимка. При смене snapshot_LabeledArchive_UsesGlobWithoutHead "
                        + "проверь publish.sh.snapshot_world.");
        assertTrue(
                snapshot.contains("archives=(\"$WORLD_BACKUPS\"/world-*-\"$snapshot_label\".tar.zst)")
                        && snapshot.contains("archive=\"${archives[0]:-}\""),
                "Архив с меткой версии выбирается globом: иначе следующий запуск публикации снова "
                        + "остановится на конвейере. При смене snapshot_LabeledArchive_UsesGlobWithoutHead "
                        + "проверь snapshot_world.");
    }

    @Test
    void rotation_LowDiskSpace_DoesNotPipeLargeArchiveListIntoHead() throws IOException {
        String source = Files.readString(SNAPSHOT);
        int start = source.indexOf("enforce_free_space()");
        int end = source.indexOf("\nmkdir -p", start);
        String rotation = source.substring(start, end);

        assertFalse(
                rotation.contains("| head -n 1"),
                "Ротация не подаёт длинный список архивов в head: иначе при pipefail это даёт SIGPIPE и "
                        + "публикация падает до перезапуска. При смене rotation_LowDiskSpace_DoesNotPipeLargeArchiveListIntoHead "
                        + "проверь world-snapshot.sh на сервере с местом меньше 10 ГБ.");
        assertTrue(
                rotation.contains("paths=(\"$WORLD_BACKUPS\"/world-*.tar.zst)")
                        && rotation.contains("path=\"${paths[0]}\""),
                "Самый старый архив выбирается shell-globом без конвейера: иначе при сотне копий "
                        + "снимок снова завершится кодом 141. При смене rotation_LowDiskSpace_DoesNotPipeLargeArchiveListIntoHead "
                        + "проверь enforce_free_space.");
    }
}
