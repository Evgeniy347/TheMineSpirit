package com.theminespirit.neoforge.log;

import com.theminespirit.core.log.EventLog;
import com.theminespirit.core.log.LogRecord;
import com.theminespirit.core.log.LogType;
import com.theminespirit.neoforge.net.BuildFingerprint;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

public final class ModLog {
    private static final Object LOCK = new Object();

    private static @Nullable EventLog log;
    private static @Nullable String build;
    private static String source = "live";

    private ModLog() {}

    public static void start(MinecraftServer server) {
        Objects.requireNonNull(server, "Сервер не может быть null: без него неизвестен каталог логов");
        synchronized (LOCK) {
            if (log != null) {
                return;
            }
            Path logs = logsDirectory(server.getWorldPath(LevelResource.ROOT));
            build = BuildFingerprint.version();
            log = new EventLog(logs.resolve("events"), build, source);
        }
    }

    static Path logsDirectory(Path worldPath) {
        Path root = Objects.requireNonNull(
                worldPath.toAbsolutePath().normalize().getParent(),
                "У папки мира нет родителя: неизвестно, куда писать logs/events");
        return root.resolve("logs");
    }

    public static boolean active() {
        synchronized (LOCK) {
            return log != null;
        }
    }

    public static LogRecord.@Nullable Builder record(LogType type, long ts, long tick) {
        synchronized (LOCK) {
            if (log == null || build == null) {
                return null;
            }
            return LogRecord.builder(type, ts, tick, build, source);
        }
    }

    public static void append(LogRecord.@Nullable Builder builder) {
        if (builder == null) {
            return;
        }
        LogRecord record = builder.build();
        synchronized (LOCK) {
            if (log != null) {
                log.append(record);
            }
        }
    }

    public static void stop() {
        close(detach());
    }

    private static @Nullable EventLog detach() {
        synchronized (LOCK) {
            EventLog current = log;
            log = null;
            build = null;
            return current;
        }
    }

    private static void close(@Nullable EventLog current) {
        if (current != null) {
            current.close();
        }
    }
}
