package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import com.theminespirit.neoforge.intellect.SettlementIntellect;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntellectForageGameTests {
    private static final BlockPos START = new BlockPos(1, 1, 1);

    private IntellectForageGameTests() {}

    private static void homeAt(GameTestHelper helper, SpiritEntity spirit, BlockPos at) {
        BlockPos absolute = helper.absolutePos(at);
        spirit.setHome(new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()));
    }

    private static SettlementIntellect intellectOf(GameTestHelper helper, SpiritEntity spirit) {
        SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());
        SpiritCoordinator coordinator = CoordinatorRegistry.get(helper.getLevel(), key);
        return coordinator.intellect();
    }

    private static void chest(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        helper.setBlock(relative.getX(), relative.getY(), relative.getZ(), Blocks.CHEST);
        ChestBlockEntity box = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        for (int slot = 0; slot < stacks.length; slot++) {
            box.setItem(slot, stacks[slot]);
        }
    }

    // [флак] охота длиннее прочих сцен интеллекта: подход к дикой корове вне домашнего загона и добивание
    @SceneStep(template = "arena", timeoutTicks = 600, coordinator = true)
    public static void forage_NoFoodWildCowWithSword_Hunts(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, START);
        homeAt(helper, spirit, START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        Cow cow = HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(8, 1, 8));
        chest(helper, new BlockPos(2, 1, 2), new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.IRON_SWORD, 2));
        boolean[] sawPrey = {false};
        String[] prey = {null};

        helper.succeedWhen(() -> {
            SettlementIntellect intellect = intellectOf(helper, spirit);
            helper.assertTrue(
                    intellect.view().isPresent(), "Интеллект ещё не собрал снимок: ждём первый пересчёт спроса");
            SettlementView view = intellect.view().orElseThrow();
            helper.assertTrue(
                    view.threats() == 0,
                    "Дикая корова не угроза: иначе поселение уходит в оборону вместо охоты, угроз " + view.threats());
            if (view.forage().hasPrey()) {
                sawPrey[0] = true;
                prey[0] = view.forage().preySpecies();
            }
            int beef = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BEEF);
            helper.assertTrue(
                    beef >= 1, "Без еды и с мечом дух обязан добыть мясо охотой: иначе поселение не переживёт голод");
            helper.assertTrue(
                    sawPrey[0], "Снимок обязан видеть дикую корову вне домашнего загона: без неё охота не знает цели");
            helper.assertTrue(
                    "cow".equals(prey[0]),
                    "Дичь — корова, а не другой вид: иначе охота ищет не того, фактически " + prey[0]);
            helper.assertTrue(!cow.isAlive(), "Дикая корова обязана погибнуть: иначе мясо взялось не от охоты");
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }
}
