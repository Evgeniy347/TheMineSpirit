package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyDangerGameTests {
    private static final int FLEE_LIMIT_TICKS = 60;
    private static final int HOLD_AFTER_FLEE_TICKS = 40;
    private static final double CREEPER_HOLD = 7.0;
    private static final double TNT_HOLD = 9.0;
    private static final double CHARGED_MOVED_AWAY = 11.0;

    private PolicyDangerGameTests() {
    }

    private static double cellDistance(GameTestHelper helper, SpiritEntity spirit, BlockPos other) {
        BlockPos cell = BlockPos.containing(helper.relativeVec(spirit.position()));
        return Math.sqrt(cell.distSqr(other));
    }

    private static Creeper idleCreeper(GameTestHelper helper, BlockPos at) {
        Creeper creeper = helper.spawn(EntityType.CREEPER, at);
        creeper.setNoAi(true);
        return creeper;
    }

    @GameTest(template = "arena", batch = "policy_creeper_near", timeoutTicks = 200)
    public static void creeper_FourBlocksAway_SpiritKeepsSevenAndDoesNotReturn(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        BlockPos creeperAt = new BlockPos(4, 1, 8);
        idleCreeper(helper, creeperAt);
        double[] closestAfter = {Double.MAX_VALUE};
        int[] farSince = {-1};
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        }, observed -> {
            double distance = cellDistance(helper, observed, creeperAt);
            if (farSince[0] < 0 && distance >= CREEPER_HOLD) {
                farSince[0] = observed.tickCount;
            }
            if (farSince[0] >= 0) {
                closestAfter[0] = Math.min(closestAfter[0], distance);
            }
            return null;
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(farSince[0] >= 0 && farSince[0] - (spirit.tickCount - scene.ticks()) <= FLEE_LIMIT_TICKS,
                    "дух обязан отойти от крипера на 7 блоков за " + FLEE_LIMIT_TICKS + " тиков, расстояние сейчас "
                            + cellDistance(helper, spirit, creeperAt));
            helper.assertTrue(spirit.tickCount >= farSince[0] + HOLD_AFTER_FLEE_TICKS, "ещё не прошло 40 тиков после отхода");
            helper.assertTrue(closestAfter[0] >= CREEPER_HOLD, "после отхода дух не должен возвращаться ближе 7 блоков, самое близкое " + closestAfter[0]);
            GameTestFixtures.reset(helper);
        });
    }

    private static Creeper chargedCreeper(GameTestHelper helper, BlockPos at) {
        Creeper creeper = idleCreeper(helper, at);
        CompoundTag tag = new CompoundTag();
        creeper.saveWithoutId(tag);
        tag.putBoolean("powered", true);
        creeper.load(tag);
        return creeper;
    }

    @GameTest(template = "arena", batch = "policy_charged_creeper", timeoutTicks = 200)
    public static void chargedCreeper_TenBlocksAway_SpiritMovesAwayAlongTheEdge(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 5));
        BlockPos creeperAt = new BlockPos(10, 1, 5);
        Creeper creeper = chargedCreeper(helper, creeperAt);
        helper.assertTrue(creeper.isPowered(), "заряженный крипер не заряжен: сцена не проверяет то, что заявлено");
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(cellDistance(helper, spirit, creeperAt) >= CHARGED_MOVED_AWAY,
                    "заряженный крипер в 10 блоках: радиус урона ~12, дух обязан отойти вдоль края хотя бы до "
                            + CHARGED_MOVED_AWAY + " блоков, сейчас " + cellDistance(helper, spirit, creeperAt));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_creeper_far", timeoutTicks = 100)
    public static void creeper_NineBlocksAway_SpiritStaysInPlace(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        idleCreeper(helper, new BlockPos(7, 1, 7));
        Vec3 start = spirit.position();
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(scene.ticks() >= 40, "ещё не прошло 40 тиков наблюдения");
            helper.assertTrue(spirit.position().distanceTo(start) < 0.3,
                    "крипер дальше 7 блоков: ложного срабатывания быть не должно, дух сместился на " + spirit.position().distanceTo(start));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_tnt", timeoutTicks = 200)
    public static void tnt_FourBlocksAwayWithEightyTickFuse_SpiritIsNineAwayAndUnharmed(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, 1, 3));
        BlockPos tntAt = new BlockPos(0, 1, 0);
        PrimedTnt[] tnt = {null};
        double[] distanceAtBlast = {-1};
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
            Vec3 absolute = helper.absoluteVec(Vec3.atBottomCenterOf(tntAt));
            tnt[0] = new PrimedTnt(helper.getLevel(), absolute.x, absolute.y, absolute.z, null);
            tnt[0].setFuse(80);
            helper.getLevel().addFreshEntity(tnt[0]);
        }, observed -> {
            if (tnt[0] != null && !tnt[0].isRemoved() && tnt[0].getFuse() <= 1) {
                distanceAtBlast[0] = cellDistance(helper, observed, tntAt);
            }
            return null;
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(tnt[0] != null && tnt[0].isRemoved(), "запал 80 тиков ещё не догорел");
            helper.assertTrue(distanceAtBlast[0] >= TNT_HOLD, "к взрыву дух обязан быть не ближе 9 блоков (радиус урона 8 и запас на смещение внутри клетки), было " + distanceAtBlast[0]);
            helper.assertTrue(scene.minHealth() >= spirit.getMaxHealth(), "дух вне радиуса взрыва: здоровье не должно меняться, минимум " + scene.minHealth());
            GameTestFixtures.reset(helper);
        });
    }

    private static void portalFrame(GameTestHelper helper) {
        for (int x = 3; x <= 6; x++) {
            helper.setBlock(x, 0, 4, Blocks.OBSIDIAN);
            helper.setBlock(x, 4, 4, Blocks.OBSIDIAN);
        }
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(3, y, 4, Blocks.OBSIDIAN);
            helper.setBlock(6, y, 4, Blocks.OBSIDIAN);
        }
        for (int x = 4; x <= 5; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(x, y, 4, Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
            }
        }
    }

    @GameTest(template = "arena", batch = "policy_portal", timeoutTicks = 300)
    public static void portal_SpiritInPortalCellWithTargetInsideIt_StepsOutAndStaysInOverworld(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        portalFrame(helper);
        helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.NETHER_PORTAL), "портал обязан стоять до начала проверки");
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        BlockPos absolute = helper.absolutePos(new BlockPos(5, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("goto", Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ())), 1);
        var home = spirit.level().dimension();

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(run.spiritTicks() >= 100, "ещё не прошло 100 тиков, тик духа " + run.spiritTicks());
            helper.assertTrue(spirit.level().dimension() == home && !spirit.isRemoved(),
                    "дух не должен переходить через портал: измерение " + spirit.level().dimension().location());
            BlockPos cell = BlockPos.containing(helper.relativeVec(spirit.position()));
            helper.assertTrue(!helper.getBlockState(cell).is(Blocks.NETHER_PORTAL) && !helper.getBlockState(cell.above()).is(Blocks.NETHER_PORTAL),
                    "через 100 тиков дух обязан стоять вне клетки портала, клетка " + cell.toShortString());
            GameTestFixtures.reset(helper);
        });
    }
}
