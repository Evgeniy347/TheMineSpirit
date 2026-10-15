package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.SpiritGear;
import com.theminespirit.neoforge.tactics.TacticsInputs;
import com.theminespirit.neoforge.tactics.WorldFactCollector;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsFactsGameTests {
    private static final int BUDGET = TacticsAdapterSettings.defaults().factChunkBudget();
    private static final int STONE_BLOCKS = 12;
    private static final int ARROWS = 16;
    private static final double STONE_SWORD_DAMAGE = 5.0;

    private TacticsFactsGameTests() {
    }

    private static Husk idleHusk(GameTestHelper helper, BlockPos at) {
        Husk husk = helper.spawn(EntityType.HUSK, at);
        husk.setNoAi(true);
        return husk;
    }

    private static EnemyFact factOf(GameTestHelper helper, List<EnemyFact> enemies, Husk husk) {
        Optional<EnemyFact> fact = enemies.stream().filter(e -> e.id().equals(husk.getUUID().toString())).findFirst();
        helper.assertTrue(fact.isPresent(), "хаск " + husk.getUUID() + " обязан попасть в факты, в фактах " + enemies);
        return fact.get();
    }

    private static TacticsInputs inputs() {
        return new TacticsInputs(CombatMode.DEFENSIVE, Optional.empty(), Optional.empty(), 1_000_000, 1_000_000,
                Optional.empty());
    }

    @GameTest(template = "arena", batch = "tactics_facts_visibility", timeoutTicks = 100)
    public static void collect_HuskInViewAndHuskBehindWall_VisibilityOnlyForTheFirst(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        Husk inView = idleHusk(helper, new BlockPos(4, 1, 8));
        Husk behindWall = idleHusk(helper, new BlockPos(0, 1, 4));
        for (int z = 3; z <= 5; z++) {
            helper.setBlock(2, 1, z, Blocks.STONE);
            helper.setBlock(2, 2, z, Blocks.STONE);
        }

        WorldFactCollector.Collected collected = new WorldFactCollector(TacticsAdapterSettings.defaults())
                .collect(spirit, inputs());

        List<EnemyFact> enemies = collected.context().enemies();
        EnemyFact seen = factOf(helper, enemies, inView);
        EnemyFact hidden = factOf(helper, enemies, behindWall);
        helper.assertTrue(seen.visible(), "хаск без преград обязан быть видим");
        helper.assertTrue(!hidden.visible(), "хаск за стеной высотой два обязан быть невидим: прямая видимость идёт через клетки мира");
        helper.assertTrue("minecraft:husk".equals(seen.type()), "тип врага — id сущности, получено " + seen.type());
        helper.assertTrue(!seen.targetingSelf(), "хаск без цели не целится в духа");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "tactics_facts_self", timeoutTicks = 100)
    public static void collect_SpiritWithSwordBowBlocks_SelfFactsFromInventory(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().addItem(new ItemStack(Items.STONE_SWORD));
        spirit.getInventory().addItem(new ItemStack(Items.BOW));
        spirit.getInventory().addItem(new ItemStack(Items.ARROW, ARROWS));
        spirit.getInventory().addItem(new ItemStack(Items.COBBLESTONE, STONE_BLOCKS));
        spirit.getInventory().addItem(new ItemStack(Items.SAND, STONE_BLOCKS));
        idleHusk(helper, new BlockPos(4, 1, 8));

        SelfFacts self = new WorldFactCollector(TacticsAdapterSettings.defaults()).collect(spirit, inputs())
                .context().self();

        helper.assertTrue(self.dps() == STONE_SWORD_DAMAGE * 20.0 / SpiritGear.ATTACK_COOLDOWN_TICKS,
                "dps духа с каменным мечом — урон меча за секунду, получено " + self.dps());
        helper.assertTrue(self.blocks() == STONE_BLOCKS, "блоков для баррикады " + STONE_BLOCKS
                + " (песок падает и не годится), получено " + self.blocks());
        helper.assertTrue(self.rangedAmmo() == ARROWS, "стрел при наличии лука " + ARROWS + ", получено " + self.rangedAmmo());
        helper.assertTrue(self.hpFraction() == 1.0, "здоровье духа полное, доля " + self.hpFraction());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "tactics_facts_budget", timeoutTicks = 100)
    public static void collect_ManyHusks_ReadsAtMostBudgetAndOneEntityQuery(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        for (int i = 0; i < 12; i++) {
            idleHusk(helper, new BlockPos(i % 9, 1, (i * 3) % 9));
        }

        WorldFactCollector.Collected collected = new WorldFactCollector(TacticsAdapterSettings.defaults())
                .collect(spirit, inputs());

        helper.assertTrue(collected.blockReads() <= BUDGET,
                "за цикл снятия фактов не больше " + BUDGET + " чтений блоков, было " + collected.blockReads());
        helper.assertTrue(collected.blockReads() > 0, "при врагах скан обязан читать мир, иначе предел ничего не доказывает");
        helper.assertTrue(collected.entityQueries() == 1, "запрос сущностей один на цикл, было " + collected.entityQueries());
        Optional<EnemyFact> nearest = collected.context().enemies().stream()
                .min(Comparator.comparingDouble(e -> e.pos().distanceTo(collected.context().self().pos())));
        helper.assertTrue(nearest.isPresent(), "ближайший враг обязан быть в фактах");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }
}
