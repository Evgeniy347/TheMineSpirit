package com.theminespirit.gametest;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class HarvestProbe {
    private static final int SAMPLE_EVERY_TICKS = 10;
    private static final int MAX_TRANSITIONS = 60;
    private static final int SCAN_XZ = 16;
    private static final int SCAN_Y = 8;

    private final GameTestHelper helper;
    private final SpiritEntity spirit;
    private final BooleanSupplier active;
    private final IntSupplier inInventory;
    private final IntSupplier inWorld;
    private final Supplier<String> dropsDetail;
    private final IntSupplier blocksLeft;
    private final List<String> samples = new ArrayList<>();
    private final List<String> transitions = new ArrayList<>();
    private boolean initialized;
    private boolean wasSuspended;
    private int workingTicks;
    private int pausedTicks;
    private int lastSampledSpiritTick = Integer.MIN_VALUE;

    private HarvestProbe(
            GameTestHelper helper,
            SpiritEntity spirit,
            BooleanSupplier active,
            IntSupplier inInventory,
            IntSupplier inWorld,
            Supplier<String> dropsDetail,
            IntSupplier blocksLeft) {
        this.helper = helper;
        this.spirit = spirit;
        this.active = active;
        this.inInventory = inInventory;
        this.inWorld = inWorld;
        this.dropsDetail = dropsDetail;
        this.blocksLeft = blocksLeft;
    }

    static HarvestProbe attach(
            GameTestHelper helper,
            SpiritEntity spirit,
            BooleanSupplier active,
            IntSupplier inInventory,
            IntSupplier inWorld,
            Supplier<String> dropsDetail,
            IntSupplier blocksLeft) {
        HarvestProbe probe = new HarvestProbe(helper, spirit, active, inInventory, inWorld, dropsDetail, blocksLeft);
        helper.onEachTick(probe::tick);
        return probe;
    }

    int workingTicks() {
        return workingTicks;
    }

    int pausedTicks() {
        return pausedTicks;
    }

    private void tick() {
        if (!active.getAsBoolean()) {
            return;
        }
        boolean suspended =
                spirit.brainIfPresent().map(brain -> brain.isSuspended()).orElse(false);
        if (!initialized) {
            wasSuspended = suspended;
            initialized = true;
        }
        if (suspended) {
            pausedTicks++;
        } else {
            workingTicks++;
        }
        if (suspended != wasSuspended) {
            if (transitions.size() < MAX_TRANSITIONS) {
                transitions.add("тик игры " + helper.getTick() + ", тик духа " + spirit.tickCount + ": "
                        + (suspended ? "заморозка" : "разморозка") + suspendReason());
            }
            wasSuspended = suspended;
        }
        if (spirit.tickCount != lastSampledSpiritTick && spirit.tickCount % SAMPLE_EVERY_TICKS == 0) {
            lastSampledSpiritTick = spirit.tickCount;
            samples.add(sample());
        }
    }

    private String suspendReason() {
        return spirit.brainIfPresent()
                .flatMap(brain -> brain.suspendReason())
                .map(reason -> " (" + reason + ")")
                .orElse("");
    }

    private String sample() {
        return "тик игры " + helper.getTick() + ", тик духа " + spirit.tickCount + ": дух "
                + spirit.blockPosition().toShortString() + ", задача "
                + spirit.taskStatus().map(Enum::name).orElse("нет") + ", мозг "
                + (spirit.brainIfPresent().map(brain -> brain.isSuspended()).orElse(false) ? "заморожен" : "тикает")
                + suspendReason() + ", тактика " + tacticsState() + ", угроза " + threat() + ", в инвентаре "
                + inInventory.getAsInt() + ", в мире " + inWorld.getAsInt() + ", блоков осталось "
                + blocksLeft.getAsInt();
    }

    private String tacticsState() {
        try {
            boolean control = spirit.tactics().wantsControl();
            return control ? "владеет (" + spirit.tactics().explain().orElse("без разбора") + ")" : "не владеет";
        } catch (RuntimeException error) {
            return "ошибка опроса: " + error;
        }
    }

    private String threat() {
        Vec3 center = spirit.position();
        AABB area = new AABB(
                center.x - SCAN_XZ,
                center.y - SCAN_Y,
                center.z - SCAN_XZ,
                center.x + SCAN_XZ,
                center.y + SCAN_Y,
                center.z + SCAN_XZ);
        List<Monster> monsters = helper.getLevel()
                .getEntitiesOfClass(Monster.class, area, monster -> monster.isAlive() && !monster.isRemoved());
        if (monsters.isEmpty()) {
            return "нет";
        }
        Monster nearest = monsters.get(0);
        double best = Double.MAX_VALUE;
        for (Monster monster : monsters) {
            double distance = monster.position().distanceTo(center);
            if (distance < best) {
                best = distance;
                nearest = monster;
            }
        }
        LivingEntity target = nearest.getTarget();
        BlockPos mobCell = nearest.blockPosition();
        return nearest.getType().toShortString() + " в " + Math.round(best) + " блоках на "
                + mobCell.toShortString() + ", цель "
                + (target == null ? "нет" : target.getType().toShortString());
    }

    String report(String failure) {
        boolean frozen =
                spirit.brainIfPresent().map(brain -> brain.isSuspended()).orElse(false);
        StringBuilder text = new StringBuilder();
        text.append("провал добычи: ").append(failure).append('\n');
        text.append("тик игры ")
                .append(helper.getTick())
                .append(", тик духа ")
                .append(spirit.tickCount)
                .append(", рабочих тиков ")
                .append(workingTicks)
                .append(", пауз ")
                .append(pausedTicks)
                .append(", дух ")
                .append(spirit.blockPosition().toShortString())
                .append(", задача ")
                .append(spirit.taskStatus().map(Enum::name).orElse("нет задачи"))
                .append(", причина ")
                .append(spirit.taskFailureReason().orElse("нет"))
                .append(", мозг ")
                .append(frozen ? "заморожен" : "тикает")
                .append(suspendReason())
                .append(", тактика ")
                .append(tacticsState())
                .append(", угроза ")
                .append(threat())
                .append('\n');
        text.append("в инвентаре ")
                .append(inInventory.getAsInt())
                .append(", в мире ")
                .append(inWorld.getAsInt())
                .append(", блоков осталось ")
                .append(blocksLeft.getAsInt())
                .append('\n');
        text.append("дроп в мире:\n").append(dropsDetail.get()).append('\n');
        text.append("переходы заморозки (").append(transitions.size()).append("):\n");
        for (String line : transitions) {
            text.append("  ").append(line).append('\n');
        }
        if (transitions.size() == MAX_TRANSITIONS) {
            text.append("  ...обрезано на ").append(MAX_TRANSITIONS).append('\n');
        }
        text.append("таймлайн (раз в ").append(SAMPLE_EVERY_TICKS).append(" тиков духа):\n");
        for (String line : samples) {
            text.append("  ").append(line).append('\n');
        }
        return text.toString();
    }
}
