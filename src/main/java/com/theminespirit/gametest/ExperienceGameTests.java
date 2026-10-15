package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.experience.SettlementExperience;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExperienceGameTests {
    private static final int TOOL_SLOT = 5;
    private static final BlockPos SPIRIT_AT = new BlockPos(1, 1, 2);
    private static final BlockPos ZOMBIE_AT = new BlockPos(2, 1, 2);
    private static final BlockPos ORE_AT = new BlockPos(2, 1, 2);
    private static final int RUNAWAY_LIMIT = 1000;
    private static final int AWARD_DELAY_TICKS = 1;
    private static final float KILL_DAMAGE = 100.0F;
    private static final float PLAYER_TAP_DAMAGE = 0.5F;

    private ExperienceGameTests() {
    }

    private static SpiritEntity spirit(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
    }

    @GameTest(template = "arena", batch = "xp_mob_kill", timeoutTicks = 200)
    public static void zombie_SpiritKillWithOnePlayerOnline_AwardsExperienceAndNoOrbs(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, ZOMBIE_AT, "xp_kill_single");
        player.totalExperience = 0;
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_AT);
        zombie.setHealth(1.0F);

        helper.assertTrue(zombie.hurt(spirit.damageSources().mobAttack(spirit), KILL_DAMAGE),
                "удар духа обязан нанести зомби смертельный урон: иначе смерть и опыт не наступят");
        helper.runAfterDelay(AWARD_DELAY_TICKS, () -> {
            helper.assertTrue(zombie.isDeadOrDying(),
                    "зомби обязан умереть от удара духа, а его здоровье " + zombie.getHealth());
            helper.assertTrue(player.totalExperience > 0,
                    "опыт от моба, убитого духом, обязан достаться реальному игроку, а его опыт "
                            + player.totalExperience);
            int orbs = orbsNear(helper).size();
            helper.assertTrue(orbs == 0,
                    "сферы опыта на землю не бросаем: опыт уходит игроку напрямую, а на земле сфер " + orbs);
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = "xp_mob_kill_two", timeoutTicks = 200)
    public static void zombie_SpiritKillWithTwoPlayersOnline_SplitsExperienceEvenly(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        ServerPlayer first = GameTestFixtures.mockPlayer(helper, ZOMBIE_AT, "xp_kill_two_a");
        ServerPlayer second = GameTestFixtures.mockPlayer(helper, new BlockPos(2, 1, 3), "xp_kill_two_b");
        first.totalExperience = 0;
        second.totalExperience = 0;
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_AT);
        zombie.setHealth(1.0F);

        helper.assertTrue(zombie.hurt(spirit.damageSources().mobAttack(spirit), KILL_DAMAGE),
                "удар духа обязан нанести зомби смертельный урон: иначе смерть и опыт не наступят");
        helper.runAfterDelay(AWARD_DELAY_TICKS, () -> {
            helper.assertTrue(first.totalExperience > 0 && second.totalExperience > 0,
                    "при двух игроках опыт обязан достаться обоим: у первого " + first.totalExperience
                            + ", у второго " + second.totalExperience);
            int diff = Math.abs(first.totalExperience - second.totalExperience);
            helper.assertTrue(diff <= 1,
                    "при двух игроках опыт делится поровну с разницей не больше 1, а разница " + diff
                            + " (первый " + first.totalExperience + ", второй " + second.totalExperience + ")");
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = "xp_block_redstone", timeoutTicks = 200)
    public static void blockBreaker_SpiritMinesRedstoneOre_AwardsExperienceAndNoOrbs(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_PICKAXE));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 3), "xp_redstone_player");
        player.totalExperience = 0;
        helper.setBlock(ORE_AT, Blocks.REDSTONE_ORE);
        BlockBreaker breaker = new BlockBreaker(spirit);
        helper.assertTrue(breaker.begin(helper.absolutePos(ORE_AT)) instanceof BlockBreaker.Started,
                "дух с киркой обязан начать ломать редстоуновую руду");
        runToEnd(breaker);

        helper.assertTrue(helper.getBlockState(ORE_AT).isAir(), "редстоуновая руда обязана быть сломана");
        helper.assertTrue(player.totalExperience >= 1 && player.totalExperience <= 5,
                "редстоуновая руда даёт 1..5 опыта (block.getExpDrop): игрок обязан получить долю в этом "
                        + "диапазоне, а получил " + player.totalExperience);
        int orbs = orbsNear(helper).size();
        helper.assertTrue(orbs == 0,
                "опыт за руду, добытую духом, уходит игроку, а не сферами на землю: сфер " + orbs);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "xp_block_diamond", timeoutTicks = 200)
    public static void blockBreaker_SpiritMinesDiamondOre_PlayerGainsAtLeastThreeExperience(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_PICKAXE));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 3), "xp_diamond_player");
        player.totalExperience = 0;
        helper.setBlock(ORE_AT, Blocks.DIAMOND_ORE);
        BlockBreaker breaker = new BlockBreaker(spirit);
        helper.assertTrue(breaker.begin(helper.absolutePos(ORE_AT)) instanceof BlockBreaker.Started,
                "дух с железной киркой обязан начать ломать алмазную руду");
        runToEnd(breaker);

        helper.assertTrue(player.totalExperience >= 3,
                "алмазная руда даёт минимум 3 опыта (UniformInt 3..7): игрок обязан получить не меньше, "
                        + "а получил " + player.totalExperience);
        int orbs = orbsNear(helper).size();
        helper.assertTrue(orbs == 0,
                "опыт за руду, добытую духом, не превращается в сферы на земле: сфер " + orbs);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "xp_pool", timeoutTicks = 200)
    public static void blockBreaker_SpiritMinesOreWithNoPlayersOnline_PoolsThenGivesOnLogin(GameTestHelper helper) {
        SettlementExperience.get(helper.getLevel().getServer()).drain();
        SpiritEntity spirit = spirit(helper);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_PICKAXE));
        helper.setBlock(ORE_AT, Blocks.REDSTONE_ORE);
        BlockBreaker breaker = new BlockBreaker(spirit);
        helper.assertTrue(breaker.begin(helper.absolutePos(ORE_AT)) instanceof BlockBreaker.Started,
                "дух с киркой обязан начать ломать редстоуновую руду");
        runToEnd(breaker);

        int pooled = SettlementExperience.get(helper.getLevel().getServer()).pending();
        helper.assertTrue(pooled >= 1 && pooled <= 5,
                "без игроков опыт за руду обязан копиться в пуле поселения (1..5 за редстоун), а в пуле " + pooled);
        helper.assertTrue(orbsNear(helper).isEmpty(),
                "без игроков опыт не бросается сферами на землю: он ждёт игрока в пуле");

        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 3), "xp_pool_player");
        helper.assertTrue(player.totalExperience >= 1,
                "при входе игрока накопленный пул обязан достаться ему, а его опыт " + player.totalExperience);
        helper.assertTrue(SettlementExperience.get(helper.getLevel().getServer()).pending() == 0,
                "после выдачи пула игроку пул обязан обнулиться, а в нём "
                        + SettlementExperience.get(helper.getLevel().getServer()).pending());
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "xp_mob_finish_two", timeoutTicks = 200)
    public static void zombie_TwoPlayerHitZombiesFinishedBySpirit_AwardsBothAndNoOrbs(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 3), "xp_finish_player");
        player.totalExperience = 0;
        Zombie first = helper.spawn(EntityType.ZOMBIE, ZOMBIE_AT);
        Zombie second = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        first.setHealth(1.0F);
        second.setHealth(1.0F);

        helper.assertTrue(first.hurt(player.damageSources().playerAttack(player), PLAYER_TAP_DAMAGE),
                "игрок обязан ранить первого зомби: без недавнего удара игрока ванильный опыт не упал бы сферами");
        helper.assertTrue(second.hurt(player.damageSources().playerAttack(player), PLAYER_TAP_DAMAGE),
                "игрок обязан ранить второго зомби: сценарий проверяет двух мобов подряд");
        int firstReward = first.getExperienceReward(helper.getLevel(), spirit);
        int secondReward = second.getExperienceReward(helper.getLevel(), spirit);
        helper.assertTrue(firstReward > 0 && secondReward > 0,
                "у зомби обязан быть положительный опыт, иначе сумма не проверит дележ");

        first.hurt(spirit.damageSources().mobAttack(spirit), KILL_DAMAGE);
        second.hurt(spirit.damageSources().mobAttack(spirit), KILL_DAMAGE);

        helper.runAfterDelay(AWARD_DELAY_TICKS, () -> {
            helper.assertTrue(first.isDeadOrDying() && second.isDeadOrDying(),
                    "оба зомби обязаны умереть от добивающих ударов духа");
            int orbs = orbsNear(helper).size();
            helper.assertTrue(orbs == 0,
                    "добитые духом мобы не бросают сферы, даже если игрок их ранил: на земле сфер " + orbs);
            helper.assertTrue(player.totalExperience == firstReward + secondReward,
                    "опыт игрока обязан равняться сумме наград двух мобов (" + (firstReward + secondReward)
                            + "), а он " + player.totalExperience);
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    private static List<ExperienceOrb> orbsNear(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area(helper));
    }

    private static AABB area(GameTestHelper helper) {
        Vec3 first = helper.absoluteVec(new Vec3(-2, 0, -2));
        Vec3 second = helper.absoluteVec(new Vec3(GameTestFixtures.FLOOR_SIZE + 2, 8, GameTestFixtures.FLOOR_SIZE + 2));
        return new AABB(first, second);
    }

    private static void runToEnd(BlockBreaker breaker) {
        int ticks = 0;
        BlockBreaker.Progress progress = BlockBreaker.Progress.BREAKING;
        while (progress == BlockBreaker.Progress.BREAKING && ticks < RUNAWAY_LIMIT) {
            progress = breaker.tick();
            ticks++;
        }
    }
}
