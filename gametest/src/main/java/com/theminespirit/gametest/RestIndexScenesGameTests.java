package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestIndexScenesGameTests {
    private RestIndexScenesGameTests() {}

    @GameTest(template = "arena", batch = "rest_index", timeoutTicks = 200)
    public static void restIndex_RejectedDuplicateJoin_KeepsLiveEntityPosition(GameTestHelper helper)
            throws IOException {
        GameTestFixtures.reset(helper);
        RestFixture rest = RestFixture.start(helper.getLevel().getServer());
        SpiritEntity ghost = null;
        try {
            SpiritEntity live = rest.spawn(helper, new BlockPos(2, 1, 2));
            BlockPos far = helper.absolutePos(new BlockPos(7, 1, 7));
            ghost = ModEntities.SPIRIT.get().create(helper.getLevel());
            helper.assertTrue(ghost != null, "Дух-копия не создан: тип сущности недоступен");
            ghost.setUUID(live.getUUID());
            ghost.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0.0F, 0.0F);
            boolean added = helper.getLevel().addFreshEntity(ghost);
            helper.assertTrue(
                    !added,
                    "Подлог не удался: мир принял вторую сущность с тем же UUID, сценарий проверяет отказ. "
                            + "Позиция живой " + live.blockPosition() + ", копии " + ghost.blockPosition());
            Helper.checkIndex(helper, rest, live, ghost);
        } finally {
            if (ghost != null) {
                ghost.discard();
            }
            rest.close();
        }
        helper.succeed();
    }

    private static final class Helper {
        private Helper() {}

        static void checkIndex(GameTestHelper helper, RestFixture rest, SpiritEntity live, SpiritEntity ghost) {
            SpiritEntity found = rest.index().find(live.getUUID()).orElse(null);
            helper.assertTrue(
                    found != null && found.equals(live),
                    "Индекс духов отдал не живую сущность мира, а отклонённую копию: /v1/bots показывает "
                            + "устаревшую позицию. Живая " + live.blockPosition() + ", копия "
                            + ghost.blockPosition() + ", индекс "
                            + (found == null ? "пусто" : found.blockPosition()));
            helper.assertTrue(
                    rest.index().snapshot().stream().allMatch(spirit -> !spirit.equals(ghost)),
                    "В снимке индекса осталась отклонённая копия: REST отдаст её позицию как позицию духа");
        }
    }
}
