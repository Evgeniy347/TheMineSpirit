package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.rest.HealthGateway;
import com.theminespirit.core.rest.HealthSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class ServerHealth implements HealthGateway {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WRITE_EVERY_TICKS = 20;

    private final Path heartbeatFile;
    private final Path debugLeaseFile;
    private final long thresholdMillis;
    private final long pid = ProcessHandle.current().pid();
    private final long startedMillis = System.currentTimeMillis();
    private volatile long lastTickMillis;
    private volatile long tick;
    private boolean writeWarning;

    public ServerHealth(Path heartbeatFile, Path debugLeaseFile, long thresholdMillis) {
        this.heartbeatFile = heartbeatFile;
        this.debugLeaseFile = debugLeaseFile;
        this.thresholdMillis = thresholdMillis;
    }

    public void onTick(MinecraftServer server) {
        lastTickMillis = System.currentTimeMillis();
        tick = server.getTickCount();
        if (tick <= 1 || tick % WRITE_EVERY_TICKS == 0) {
            writeHeartbeat();
        }
    }

    @Override
    public HealthSnapshot snapshot(long nowMillis) {
        return new HealthSnapshot(nowMillis, lastTickMillis, tick, thresholdMillis, debugPaused(nowMillis / 1000));
    }

    private void writeHeartbeat() {
        String content =
                "pid=" + pid + "\nstarted_ms=" + startedMillis + "\ntick=" + tick + "\nnow_ms=" + lastTickMillis + "\n";
        try {
            Path parent = heartbeatFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = heartbeatFile.resolveSibling(heartbeatFile.getFileName() + ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, heartbeatFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (writeWarning) {
                LOGGER.warn("Файл-сердцебиение снова пишется: {}", heartbeatFile);
                writeWarning = false;
            }
        } catch (IOException e) {
            if (!writeWarning) {
                LOGGER.warn(
                        "Файл-сердцебиение {} не пишется ({}): сторож зависания потеряет резервный признак, "
                                + "проверьте каталог и права",
                        heartbeatFile,
                        e.toString());
                writeWarning = true;
            }
        }
    }

    private boolean debugPaused(long nowSeconds) {
        if (!Files.isRegularFile(debugLeaseFile)) {
            return false;
        }
        try {
            String first = Files.readString(debugLeaseFile, StandardCharsets.UTF_8)
                    .lines()
                    .findFirst()
                    .orElse("");
            int firstBar = first.indexOf('|');
            if (firstBar < 0) {
                return false;
            }
            int nextBar = first.indexOf('|', firstBar + 1);
            String untilText = first.substring(firstBar + 1, nextBar < 0 ? first.length() : nextBar)
                    .trim();
            long until = Long.parseLong(untilText);
            return until > nowSeconds;
        } catch (IOException | NumberFormatException e) {
            LOGGER.debug(
                    "Файл замка живой отладки {} не прочитан ({}): пауза отладки не учитывается",
                    debugLeaseFile,
                    e.toString());
            return false;
        }
    }
}
