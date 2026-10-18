package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UseBlockGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final long NOON = 6000;
    private static final float FULL_HP = 20.0F;
    private static final int PASSIVE_OBSERVED_TICKS = 100;

    private UseBlockGameTests() {
    }

    @GameTest(template = "arena", batch = "useblock_lever", timeoutTicks = 200)
    public static void useBlock_LeverOnSupportBesideLamp_LampLights(GameTestHelper helper) {
        MovementScene.prepare(helper);
        GameTestFixtures.holdDayTime(helper, NOON);
        BlockPos lever = new BlockPos(5, 2, 5);
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, false));
        helper.setBlock(new BlockPos(6, 2, 5), Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, false));

        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(4, 1, 5));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.pointCall(helper, "use_block", lever), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "use_block обязан нажать рычаг: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(lever).getValue(BlockStateProperties.POWERED),
                    "рычаг обязан переключиться в положение «включён»");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 2, 5)).getValue(RedstoneLampBlock.LIT),
                    "лампа рядом с включённым рычагом обязана загореться: нажатие передаёт питание соседям");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "open_door", timeoutTicks = 300)
    public static void open_OakDoor_OpensThenCloseDoorCloses(GameTestHelper helper) {
        MovementScene.prepare(helper);
        GameTestFixtures.holdDayTime(helper, NOON);
        BlockPos lower = new BlockPos(5, 1, 5);
        placeDoor(helper, lower);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(5, 1, 7));
        int[] phase = {0};
        boolean[] opened = {false};
        boolean[] closeRan = {false};

        helper.onEachTick(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            switch (phase[0]) {
                case 0 -> {
                    if (spirit.tickCount >= SETTLE_TICKS) {
                        spirit.startTask(MovementScene.pointCall(helper, "open", lower));
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    if (status == RunStatus.SUCCEEDED) {
                        opened[0] = isOpen(helper, lower) && isOpen(helper, lower.above());
                        spirit.startTask(MovementScene.pointCall(helper, "close_door", lower));
                        phase[0] = 2;
                    }
                }
                case 2 -> {
                    if (status == RunStatus.RUNNING) {
                        closeRan[0] = true;
                    } else if (closeRan[0] && status == RunStatus.SUCCEEDED) {
                        phase[0] = 3;
                    }
                }
                default -> {
                }
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(phase[0] == 3,
                    "сцена не дошла до закрытия двери: фаза " + phase[0] + ", статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(opened[0],
                    "open обязан открыть обе створки двери, иначе дух пройдёт в половину проёма");
            helper.assertTrue(!isOpen(helper, lower) && !isOpen(helper, lower.above()),
                    "close_door обязан закрыть дверь после open");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "setmode_passive", timeoutTicks = 300)
    public static void setCombatMode_Passive_SpiritNeverStrikesAttackingHusk(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        BlockPos huskAt = new BlockPos(5, 1, 10);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        SpiritRun mode = SpiritRun.launch(helper, spirit,
                MovementScene.call("set_combat_mode", Map.of("mode", "passive")), SETTLE_TICKS);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, FULL_HP);
        boolean[] retreated = {false};
        helper.onEachTick(() -> {
            if (scene.started()) {
                retreated[0] |= spirit.tactics().lastOutcome()
                        .map(outcome -> outcome.decision() == TacticalOption.RETREAT).orElse(false);
            }
        });

        helper.succeedWhen(() -> {
            mode.assertStarted();
            scene.assertStarted();
            helper.assertTrue(spirit.tactics().combatMode() == CombatMode.PASSIVE,
                    "set_combat_mode обязан выставить режим passive, а стоит " + spirit.tactics().combatMode());
            helper.assertTrue(scene.ticks() >= PASSIVE_OBSERVED_TICKS,
                    "наблюдение за пассивным духом ещё идёт: " + scene.ticks());
            helper.assertTrue(retreated[0],
                    "пассивный дух обязан отходить от нападающего, иначе тест ничего не доказывает");
            helper.assertTrue(spirit.tactics().combat().strikes() == 0,
                    "PASSIVE не бьёт никогда: ударов " + spirit.tactics().combat().strikes());
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(),
                    "враг не ранен, здоровье " + husk.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }

    private static void placeDoor(GameTestHelper helper, BlockPos lower) {
        helper.setBlock(lower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(BlockStateProperties.OPEN, false));
        helper.setBlock(lower.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(BlockStateProperties.OPEN, false));
    }

    private static boolean isOpen(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(BlockStateProperties.OPEN);
    }
}
