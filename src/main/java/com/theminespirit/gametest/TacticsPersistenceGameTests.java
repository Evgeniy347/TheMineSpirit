package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.adapter.StoredTactics;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.SpiritTacticsStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsPersistenceGameTests {
    private static final int ZONE_RADIUS = 6;
    private static final int TICKS_BEFORE_SAVE = 15;
    private static final int TICKS_AFTER_LOAD = 15;

    private TacticsPersistenceGameTests() {
    }

    private static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.getLevel().addFreshEntity(reloaded);
        return reloaded;
    }

    @GameTest(template = "arena", batch = "tactics_persist_roundtrip", timeoutTicks = 200)
    public static void save_SpiritWithOrdersAndMemory_ReloadedKeepsModeZoneWardAndMemory(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        BlockPos zoneCenter = helper.absolutePos(new BlockPos(2, 1, 2));
        spirit.tactics().setGuardZone(new BlockCoord(zoneCenter.getX(), zoneCenter.getY(), zoneCenter.getZ()), ZONE_RADIUS);
        spirit.tactics().setCombatMode(CombatMode.GUARD_AREA);
        SpiritEntity ward = TacticsScene.spirit(helper, new BlockPos(7, 1, 7));
        spirit.tactics().setWard(ward.getUUID());
        TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 9));

        helper.runAfterDelay(TICKS_BEFORE_SAVE, () -> {
            StoredTactics before = spirit.tactics().snapshot();
            helper.assertTrue(before.memory().isPresent(), "после нескольких оценок у духа есть память тактики");
            CompoundTag tag = new CompoundTag();
            spirit.saveWithoutId(tag);
            helper.assertTrue(SpiritTacticsStore.read(tag).isPresent(), "состояние тактики записано в ключ " + SpiritTacticsStore.KEY);
            SpiritEntity reloaded = reload(helper, spirit, tag);
            helper.assertTrue(reloaded.tactics().combatMode() == CombatMode.GUARD_AREA,
                    "приказанный режим сохранён: " + reloaded.tactics().combatMode());
            StoredTactics after = reloaded.tactics().snapshot();
            helper.assertTrue(after.zone().equals(before.zone()), "зона охраны сохранена: было " + before.zone() + ", стало " + after.zone());
            helper.assertTrue(after.ward().equals(before.ward()), "подопечный сохранён: было " + before.ward() + ", стало " + after.ward());
            helper.assertTrue(after.memory().equals(before.memory()), "память тактики сохранена без потерь");
            helper.runAfterDelay(TICKS_AFTER_LOAD, () -> {
                helper.assertTrue(reloaded.tactics().failures() == 0, "после загрузки тактика работает: " + reloaded.tactics().lastFailure().orElse("нет"));
                helper.assertTrue(reloaded.tactics().stepCalls() > 0, "после загрузки тактика оценивает обстановку");
                reloaded.discard();
                ward.discard();
                GameTestFixtures.finish(helper);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", batch = "tactics_persist_corrupt", timeoutTicks = 200)
    public static void load_CorruptTacticsState_ReportsFailureAndStartsWithDefaults(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        SpiritTacticsStore.write(tag, "{\"version\":1,\"ordered\":\"BERSERK\"}");
        SpiritEntity reloaded = reload(helper, spirit, tag);

        helper.runAfterDelay(TICKS_AFTER_LOAD, () -> {
            helper.assertTrue(reloaded.tactics().restoreFailure().isPresent(),
                    "повреждённое состояние тактики не должно пропадать молча: причина обязана быть записана");
            helper.assertTrue(reloaded.tactics().restoreFailure().get().contains("BERSERK"),
                    "причина называет испорченное значение: " + reloaded.tactics().restoreFailure().orElse("нет"));
            helper.assertTrue(reloaded.tactics().combatMode() == CombatMode.DEFENSIVE, "режим после сбоя — по умолчанию DEFENSIVE");
            helper.assertTrue(reloaded.isAlive() && reloaded.tactics().failures() == 0, "дух жив и тактика работает");
            reloaded.discard();
            GameTestFixtures.finish(helper);
            helper.succeed();
        });
    }
}
