package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.forestry.TreeSpecies;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChopTreeGameTests {
    private ChopTreeGameTests() {
    }

    @GameTest(template = "empty", batch = "chop_registry", timeoutTicks = 20)
    public static void treeSpecies_AllIds_ExistInRegistries(GameTestHelper helper) {
        for (TreeSpecies species : TreeSpecies.ALL) {
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(species.logId())),
                    "порода '" + species.id() + "': блок брёвен '" + species.logId() + "' отсутствует в реестре "
                            + "блоков — опечатка в id молча дала бы 'деревьев нет'");
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(species.leavesId())),
                    "порода '" + species.id() + "': блок листьев '" + species.leavesId() + "' отсутствует в реестре "
                            + "блоков — дерево не распозналось бы как настоящее");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(species.saplingId())),
                    "порода '" + species.id() + "': саженец '" + species.saplingId() + "' отсутствует в реестре "
                            + "предметов — посадка после рубки не смогла бы его поставить");
        }
        helper.succeed();
    }
}
