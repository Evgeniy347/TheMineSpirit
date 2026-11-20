package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.log.LogCause;
import com.theminespirit.core.movement.SpinDetector;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.log.SpiritWorldLog;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SpinWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, SpinDetector> DETECTORS = new ConcurrentHashMap<>();
    private static final Map<UUID, ScriptAge> SCRIPTS = new ConcurrentHashMap<>();

    private record ScriptAge(String script, long since) {}

    private SpinWatch() {}

    public static void tick(Mob mob, String owner, long tick) {
        SpinDetector detector = DETECTORS.computeIfAbsent(mob.getUUID(), ignored -> new SpinDetector());
        String script =
                mob instanceof SpiritTaskView view ? view.currentTaskScript().orElse("-") : "-";
        ScriptAge age = SCRIPTS.compute(mob.getUUID(), (id, old) -> switchAge(old, script, tick));
        SpinDetector.Spin spin = detector.feed(tick, mob.getX(), mob.getZ(), mob.getYRot());
        if (spin == null) {
            return;
        }
        BlockPos feet = mob.blockPosition();
        BlockPos target = mob.getNavigation().getTargetPos();
        Path path = mob.getNavigation().getPath();
        String next = path == null || path.isDone() ? "-" : shortCoord(path.getNextNodePos());
        String layer = "-".equals(next) ? "RouteWalk" : "ванильный";
        String nav = path == null ? "нет" : path.isDone() ? "конец" : "идёт";
        String look = String.format(
                Locale.ROOT,
                "%.1f,%.1f,%.1f",
                mob.getLookControl().getWantedX(),
                mob.getLookControl().getWantedY(),
                mob.getLookControl().getWantedZ());
        LOGGER.info(
                "[spin] дух {} ({}): {}, вид={}, слой={}, script={}, возраст={} тиков, nav={}, look={}, target={}, next={}",
                mob.getName().getString(),
                owner,
                spin.text(),
                spin.kind(),
                layer,
                script,
                tick - age.since(),
                nav,
                look,
                shortCoord(target),
                next);
        if (mob.level() instanceof ServerLevel server) {
            SpiritWorldLog.stuck(
                    server,
                    new SpiritWorldLog.Stuck(
                            mob.getUUID(),
                            "spin",
                            new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                            script,
                            null,
                            "кружение: " + spin.text() + "; вид=" + spin.kind() + "; next=" + next,
                            LogCause.STUCK,
                            spin.startTick()));
        }
    }

    private static ScriptAge switchAge(@Nullable ScriptAge old, String script, long tick) {
        if (old == null) {
            return new ScriptAge(script, tick);
        }
        if ("-".equals(script) || old.script().equals(script)) {
            return old;
        }
        return new ScriptAge(script, tick);
    }

    private static String shortCoord(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
