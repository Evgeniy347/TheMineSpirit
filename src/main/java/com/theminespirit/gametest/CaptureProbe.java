package com.theminespirit.gametest;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Диагностика флака отлова: тест падает по «животное ушло», а по одному сообщению не видно,
// кольцо было не достроено, овца вышла через дыру или дух стоял замороженным. Проба копит
// таймлайн (позиция овцы раз в 10 тиков), порядок постановки блоков кольца, состояние духа
// (taskStatus, brain.suspendReason, тактика) и ближайшую угрозу, чтобы провал объяснял себя.
final class CaptureProbe {
    private static final int SAMPLE_EVERY_TICKS = 10;
    private static final int SCAN_XZ = 9;
    private static final int SCAN_Y_TOP = 5;

    private final GameTestHelper helper;
    private final SpiritEntity spirit;
    private final List<String> samples = new ArrayList<>();
    private final List<String> placedOrder = new ArrayList<>();
    private final List<BlockPos> placedBlocks = new ArrayList<>();

    private CaptureProbe(GameTestHelper helper, SpiritEntity spirit) {
        this.helper = helper;
        this.spirit = spirit;
    }

    static CaptureProbe attach(GameTestHelper helper, SpiritEntity spirit) {
        CaptureProbe probe = new CaptureProbe(helper, spirit);
        helper.onEachTick(probe::tick);
        return probe;
    }

    private void tick() {
        recordNewBlocks();
        if (spirit.tickCount % SAMPLE_EVERY_TICKS == 0) {
            samples.add("тик духа " + spirit.tickCount + ": овца " + sheepPosition() + ", блоков кольца "
                    + placedBlocks.size() + ", дух " + spiritPosition() + ", задача "
                    + spirit.taskStatus().map(Enum::name).orElse("нет") + ", мозг "
                    + (spirit.brainIfPresent().map(brain -> brain.isSuspended()).orElse(false) ? "заморожен" : "тикает")
                    + spirit.brainIfPresent().flatMap(brain -> brain.suspendReason()).map(reason -> " (" + reason + ")")
                            .orElse("")
                    + ", тактика " + tacticsState() + ", угроза " + threat());
        }
    }

    private void recordNewBlocks() {
        for (int x = 0; x < SCAN_XZ; x++) {
            for (int z = 0; z < SCAN_XZ; z++) {
                for (int y = 1; y <= SCAN_Y_TOP; y++) {
                    BlockPos relative = new BlockPos(x, y, z);
                    if (placedBlocks.contains(relative)) {
                        continue;
                    }
                    var state = helper.getLevel().getBlockState(helper.absolutePos(relative));
                    if (state.is(Blocks.DIRT) || state.is(Blocks.COBBLESTONE)) {
                        placedBlocks.add(relative);
                        placedOrder.add("тик " + spirit.tickCount + " -> " + relative.toShortString());
                    }
                }
            }
        }
    }

    private String sheepPosition() {
        Sheep sheep = onlySheep();
        if (sheep == null) {
            return "нет живой овцы";
        }
        BlockPos pos = sheep.blockPosition();
        return pos.toShortString() + " (от спавна " + deltaFromSpawn(pos) + ")";
    }

    private String deltaFromSpawn(BlockPos pos) {
        int dx = pos.getX() - 4;
        int dz = pos.getZ() - 4;
        return "dx=" + dx + ", dz=" + dz;
    }

    private String spiritPosition() {
        BlockPos pos = spirit.blockPosition();
        return pos.toShortString();
    }

    private String tacticsState() {
        try {
            boolean control = spirit.tactics().wantsControl();
            return control ? "владеет (" + spirit.tactics().explain().orElse("без разбора") + ")"
                    : "не владеет";
        } catch (RuntimeException error) {
            return "ошибка опроса: " + error;
        }
    }

    private String threat() {
        Vec3 center = spirit.position();
        AABB area = new AABB(center.x - 16, center.y - 8, center.z - 16, center.x + 16, center.y + 8, center.z + 16);
        List<Monster> monsters = helper.getLevel().getEntitiesOfClass(Monster.class, area,
                monster -> monster.isAlive() && !monster.isRemoved());
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
        return nearest.getType().toShortString() + " в " + Math.round(best) + " блоках, цель "
                + (target == null ? "нет" : target.getType().toShortString());
    }

    private Sheep onlySheep() {
        Vec3 first = helper.absoluteVec(new Vec3(-2, 0, -2));
        Vec3 second = helper.absoluteVec(new Vec3(11, 8, 11));
        List<Sheep> sheep = helper.getLevel().getEntitiesOfClass(Sheep.class, new AABB(first, second),
                entity -> !entity.isRemoved() && entity.isAlive());
        return sheep.size() == 1 ? sheep.get(0) : null;
    }

    String report(String failure) {
        StringBuilder text = new StringBuilder();
        text.append("провал отлова: ").append(failure).append('\n');
        text.append("блоков кольца к моменту провала: ").append(placedBlocks.size()).append('\n');
        text.append("порядок постановки (").append(placedOrder.size()).append("):\n");
        for (String line : placedOrder) {
            text.append("  ").append(line).append('\n');
        }
        text.append("таймлайн овцы (раз в ").append(SAMPLE_EVERY_TICKS).append(" тиков):\n");
        for (String line : samples) {
            text.append("  ").append(line).append('\n');
        }
        return text.toString();
    }
}