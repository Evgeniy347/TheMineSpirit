package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class MoveTrace {
    static final double MIN_SHIFT = 0.3;
    static final int IDLE_TICKS = 100;
    static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int FLUSH_TICKS = 20;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT);
    private static final Path FILE = Path.of("logs", "moves.log");
    private static final Path OLD = Path.of("logs", "moves.1.log");
    private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();
    private static @Nullable BufferedWriter writer;
    private static long written;
    private static long flushedAt;
    private static boolean broken;
    private static final Object WRITE_LOCK = new Object();

    private record Last(
            Vec3 pos, @Nullable BlockPos target, @Nullable BlockPos next, long tick) {}

    private MoveTrace() {}

    public static void record(Mob spirit, String owner, long tick) {
        record(spirit, spirit.getName().getString(), owner, tick);
    }

    public static void record(Mob spirit, String name, String owner, long tick) {
        if (broken || spirit.level().isClientSide()) {
            return;
        }
        Vec3 pos = spirit.position();
        var navigation = spirit.getNavigation();
        BlockPos target = navigation.getTargetPos();
        var path = navigation.getPath();
        BlockPos next = path == null || path.isDone() ? null : path.getNextNodePos();
        Last last = LAST.get(spirit.getUUID());
        if (last != null
                && last.pos().distanceToSqr(pos) < MIN_SHIFT * MIN_SHIFT
                && same(last.target(), target)
                && same(last.next(), next)
                && tick - last.tick() < IDLE_TICKS) {
            return;
        }
        LAST.put(spirit.getUUID(), new Last(pos, target, next, tick));
        Vec3 motion = spirit.getDeltaMovement();
        Node node = path == null || path.isDone() ? null : path.getNextNode();
        String line = String.format(
                Locale.ROOT,
                "%s t=%d %s pos=%.2f,%.2f,%.2f v=%.2f,%.2f,%.2f ground=%s water=%s target=%s next=%s%s path=%s owner=%s%n",
                LocalTime.now(ZoneId.systemDefault()).format(TIME),
                tick,
                name,
                pos.x,
                pos.y,
                pos.z,
                motion.x,
                motion.y,
                motion.z,
                spirit.onGround(),
                spirit.isInWater(),
                coord(target),
                coord(next),
                node == null ? "" : "(" + node.type + ")",
                path == null ? "-" : path.getNextNodeIndex() + "/" + path.getNodeCount(),
                owner);
        write(line, tick);
    }

    private static void write(String line, long tick) {
        synchronized (WRITE_LOCK) {
            try {
                if (writer == null || written > MAX_BYTES) {
                    open();
                }
                BufferedWriter out = writer;
                if (out == null) {
                    return;
                }
                out.write(line);
                written += line.length();
                if (tick - flushedAt >= FLUSH_TICKS || tick < flushedAt) {
                    out.flush();
                    flushedAt = tick;
                }
            } catch (IOException failure) {
                broken = true;
                LOGGER.error(
                        "[move-trace] трасса перемещений отключена: запись в {} не удалась: {}",
                        FILE,
                        failure.toString());
            }
        }
    }

    private static void open() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
            Files.move(FILE, OLD, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.createDirectories(FILE.getParent());
        written = Files.exists(FILE) ? Files.size(FILE) : 0;
        writer = Files.newBufferedWriter(
                FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static boolean same(@Nullable BlockPos first, @Nullable BlockPos second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String coord(@Nullable BlockPos pos) {
        return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
