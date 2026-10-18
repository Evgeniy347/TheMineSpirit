package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.ClimbRoute;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ClimbParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ClimbScript implements ScriptInstance {
    public static final double CLIMB_SPEED = 0.2;
    public static final double HORIZONTAL_SPEED = 0.15;
    public static final double ARRIVAL = 0.05;
    public static final int STAND_ON_COLUMN_TICKS = 100;
    private static final int COLUMN_SCAN_DOWN = 8;
    private static final int COLUMN_SCAN_UP = 64;
    private static final int[][] NEIGHBOURS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final SpiritEntity spirit;
    private final boolean down;
    private int columnX;
    private int columnZ;
    private int targetY;
    private double startY;
    private ClimbRoute.Span span;
    private boolean planned;
    private int ticks;
    private int standTicks;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private ScriptResult result;

    public ClimbScript(SpiritEntity spirit, ClimbParams params) {
        this.spirit = spirit;
        this.down = params.down();
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока лез по колонне");
        }
        ticks++;
        if (!planned && !plan()) {
            return outcome;
        }
        if (!onColumn()) {
            if (++standTicks > STAND_ON_COLUMN_TICKS) {
                return fail("дух не встал на колонну (" + columnX + ", " + columnZ + ") за " + STAND_ON_COLUMN_TICKS
                        + " тиков: лестница у стены, но подойти к ней не выходит");
            }
            stepOntoColumn();
            return ScriptStatus.RUNNING;
        }
        standTicks = 0;
        if (reached()) {
            return succeed();
        }
        if (ticks >= CatalogLimits.CLIMB_TIMEOUT_TICKS) {
            return fail("не долез до y=" + targetY + " за " + ticks + " тиков (тайм-аут "
                    + CatalogLimits.CLIMB_TIMEOUT_TICKS + "), сейчас y=" + Math.round(spirit.getY()));
        }
        climb();
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        spirit.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void suspend() {
        spirit.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private boolean plan() {
        BlockPos feet = spirit.blockPosition();
        Optional<BlockPos> rung = findRung(feet);
        if (rung.isEmpty()) {
            fail("рядом нет лестницы, лианы или строительных лесов: дух стоит не у колонны, по которой можно лезть");
            return false;
        }
        BlockPos at = rung.get();
        columnX = at.getX();
        columnZ = at.getZ();
        Level level = spirit.level();
        int low = Math.max(level.getMinBuildHeight(), feet.getY() - COLUMN_SCAN_DOWN);
        int high = Math.min(level.getMaxBuildHeight() - 1, feet.getY() + COLUMN_SCAN_UP);
        List<Integer> rungs = new ArrayList<>();
        for (int y = low; y <= high; y++) {
            if (climbable(level, columnX, y, columnZ)) {
                rungs.add(y);
            }
        }
        span = ClimbRoute.span(rungs, feet.getY()).orElseThrow(
                () -> new IllegalStateException("Колонна " + columnX + ", " + columnZ
                        + " нашлась по клетке-ступени, но список ступеней пуст: проверьте сбор высот"));
        targetY = span.targetY(down);
        startY = spirit.getY();
        planned = true;
        return true;
    }

    private boolean reached() {
        double y = spirit.getY();
        return down ? y <= targetY + ARRIVAL : y >= targetY - ARRIVAL;
    }

    private boolean onColumn() {
        BlockPos feet = spirit.blockPosition();
        return spirit.onClimbable() && feet.getX() == columnX && feet.getZ() == columnZ;
    }

    private void stepOntoColumn() {
        double dx = columnX + 0.5 - spirit.getX();
        double dz = columnZ + 0.5 - spirit.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6) {
            spirit.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double speed = Math.min(HORIZONTAL_SPEED, length);
        spirit.setDeltaMovement(dx / length * speed, 0, dz / length * speed);
    }

    private void climb() {
        double dx = clamp((columnX + 0.5 - spirit.getX()) * 0.5);
        double dz = clamp((columnZ + 0.5 - spirit.getZ()) * 0.5);
        spirit.setDeltaMovement(dx, down ? -CLIMB_SPEED : CLIMB_SPEED, dz);
    }

    private Optional<BlockPos> findRung(BlockPos feet) {
        Level level = spirit.level();
        for (int dy : new int[] {0, 1, -1}) {
            for (int[] offset : NEIGHBOURS) {
                BlockPos candidate = feet.offset(offset[0], dy, offset[1]);
                if (climbable(level, candidate.getX(), candidate.getY(), candidate.getZ())) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean climbable(Level level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.CLIMBABLE);
    }

    private static double clamp(double value) {
        return Math.max(-HORIZONTAL_SPEED, Math.min(HORIZONTAL_SPEED, value));
    }

    private ScriptStatus succeed() {
        int climbed = (int) Math.round(Math.abs(spirit.getY() - startY));
        result = new ScriptResult.Data(Map.of(
                "direction", down ? "down" : "up",
                "blocks", climbed,
                "y", targetY));
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        return outcome;
    }
}
