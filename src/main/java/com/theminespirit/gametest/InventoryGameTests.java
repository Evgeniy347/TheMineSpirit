package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InventoryGameTests {
    private InventoryGameTests() {
    }

    @GameTest(template = "arena", batch = "inv_access_single", timeoutTicks = 200)
    public static void containerAccess_SingleChest_Opens27SlotChest(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 4)));

        helper.assertTrue(open instanceof ContainerAccess.Opened opened && opened.container().getContainerSize() == 27,
                "одиночный сундук обязан открываться как контейнер на 27 слотов, фактически " + describe(open));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "inv_access_double", timeoutTicks = 200)
    public static void containerAccess_AdjacentChests_OpenAsDouble(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 4)));

        helper.assertTrue(open instanceof ContainerAccess.Opened opened && opened.container().getContainerSize() == 54,
                "два соседних сундука обязаны открываться как двойной на 54 слота, фактически " + describe(open));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "inv_access_kinds", timeoutTicks = 200)
    public static void containerAccess_BarrelAndShulker_Open(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(5, 1, 3, Blocks.BARREL);
        helper.setBlock(6, 1, 5, Blocks.WHITE_SHULKER_BOX);
        ContainerAccess.Open barrel = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 3)));
        ContainerAccess.Open shulker = ContainerAccess.open(helper.getLevel(),
                helper.absolutePos(new BlockPos(6, 1, 5)));

        helper.assertTrue(barrel instanceof ContainerAccess.Opened opened
                && opened.container().getContainerSize() == 27,
                "бочка обязана открываться как контейнер на 27 слотов, фактически " + describe(barrel));
        helper.assertTrue(shulker instanceof ContainerAccess.Opened opened
                && opened.container().getContainerSize() == 27,
                "шалкер-бокс обязан открываться как контейнер на 27 слотов, фактически " + describe(shulker));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "inv_access_forbidden", timeoutTicks = 200)
    public static void containerAccess_FurnaceHopperDispenserEnder_Refused(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(2, 1, 2, Blocks.FURNACE);
        helper.setBlock(3, 1, 2, Blocks.HOPPER);
        helper.setBlock(4, 1, 2, Blocks.DISPENSER);
        helper.setBlock(5, 1, 2, Blocks.ENDER_CHEST);

        for (int x = 2; x <= 5; x++) {
            ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(),
                    helper.absolutePos(new BlockPos(x, 1, 2)));
            helper.assertTrue(open instanceof ContainerAccess.Refused refused && !refused.reason().isBlank(),
                    "печи, воронки, раздатчики и эндер-сундук запрещены: блок в (" + x + ",1,2) обязан дать отказ "
                            + "с пояснением, фактически " + describe(open));
        }
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "inv_access_loot", timeoutTicks = 200)
    public static void containerAccess_ChestWithUnrevealedLootTable_Refused(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(6, 1, 4));
        RandomizableContainerBlockEntity entity =
                (RandomizableContainerBlockEntity) helper.getLevel().getBlockEntity(chest);
        entity.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.parse("minecraft:chests/simple_dungeon")));
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), chest);

        helper.assertTrue(open instanceof ContainerAccess.Refused refused && refused.reason().contains("добыч"),
                "сундук структуры с нераскрытой таблицей добычи обязан дать отказ с пояснением про добычу, "
                        + "иначе дух забрал бы чужую добычу, фактически " + describe(open));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "inv_mapper_variant", timeoutTicks = 200)
    public static void stackMapper_DamagedPickaxe_SplitsVariant(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SimpleContainer container = new SimpleContainer(4);
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(10);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        container.setItem(1, new ItemStack(Items.COBBLESTONE, 7));
        container.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        container.setItem(3, damaged);

        StackMapper mapper = new StackMapper();
        List<Stack> stacks = mapper.map(container);

        helper.assertTrue(stacks.size() == 4,
                "снимок обязан повторять слоты контейнера один в один: 4 слота дали " + stacks.size());
        helper.assertTrue(stacks.get(0) != null && stacks.get(1) != null
                && stacks.get(0).sameKindAs(stacks.get(1)),
                "две стопки обычного булыжника обязаны иметь один вариант и сливаться сортёром");
        helper.assertTrue(stacks.get(2) != null && stacks.get(3) != null
                && !stacks.get(2).sameKindAs(stacks.get(3)),
                "целая и повреждённая кирки обязаны получить разные варианты: иначе сортёр сольёт зачарованные "
                        + "и повреждённые предметы с обычными и игрок потеряет вещь");
        helper.assertTrue(mapper.sampleFor(stacks.get(0).variant()).is(Items.COBBLESTONE),
                "образец варианта обязан хранить предмет для обратной раскладки в контейнер");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static String describe(ContainerAccess.Open open) {
        return switch (open) {
            case ContainerAccess.Opened opened ->
                    "открыт контейнер на " + opened.container().getContainerSize() + " слотов";
            case ContainerAccess.Refused refused -> "отказ: " + refused.reason();
        };
    }
}
