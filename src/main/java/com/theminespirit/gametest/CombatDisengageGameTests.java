package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatDisengageGameTests {
    private static final int DISTANCE = 6;
    private static final BlockPos SPIRIT_AT = new BlockPos(5, 1, 5);
    private static final BlockPos ZOMBIE_AT = new BlockPos(8, 1, 5);
    private static final float TOUGH_HP = 200;
    private static final int HEALTH_SETTLE_TICKS = 5;
    private static final int DISENGAGE_LIMIT_TICKS = 200;

    private CombatDisengageGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void disengage_FightingZombie_BacksOffAndStopsAttacking(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, SPIRIT_AT);
        Zombie zombie = TacticsScene.watcher(helper, EntityType.ZOMBIE, ZOMBIE_AT);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TOUGH_HP);
        zombie.setHealth(TOUGH_HP);
        zombie.setTarget(spirit);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                MovementScene.call("disengage", Map.of("distance", DISTANCE)),
                TacticsScene.SETTLE_TICKS);
        double[] healthAtStart = {-1};
        helper.onEachTick(() -> {
            if (run.started() && healthAtStart[0] < 0 && run.spiritTicks() >= HEALTH_SETTLE_TICKS) {
                healthAtStart[0] = zombie.getHealth();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "выход из боя обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = spirit.position().distanceTo(zombie.position());
            helper.assertTrue(
                    distance >= DISTANCE,
                    "дух обязан отойти от врага минимум на " + DISTANCE + " блоков, а он в " + distance);
            helper.assertTrue(
                    spirit.getTarget() == null,
                    "disengage обязан прекратить бой: цель духа обязана стать пустой, а она " + spirit.getTarget());
            helper.assertTrue(
                    healthAtStart[0] >= 0 && zombie.getHealth() >= healthAtStart[0],
                    "после выхода из боя дух не атакует: здоровье врага не должно падать, было " + healthAtStart[0]
                            + ", стало " + zombie.getHealth());
            helper.assertTrue(
                    run.spiritTicks() <= DISENGAGE_LIMIT_TICKS,
                    "выход из боя обязан уложиться в " + DISENGAGE_LIMIT_TICKS + " тиков духа, ушло "
                            + run.spiritTicks());
            TacticsScene.finish(helper, spirit);
        });
    }
}
