package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.DecisionSnapshot;
import com.theminespirit.core.tactics.adapter.DecisionText;
import com.theminespirit.core.tactics.adapter.ReasonFactor;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsJournalGameTests {
    private static final float FULL_HP = 20.0F;
    private static final int OBSERVED_TICKS = 80;
    private static final int HUSK_BLOCKS = 5;
    private static final String HUSK_TYPE = "minecraft:husk";
    private static final int LIMIT = TacticsService.DECISIONS_IN_CHAT;

    private TacticsJournalGameTests() {
    }

    private static SpiritEntity swordsman(GameTestHelper helper, BlockPos at) {
        SpiritEntity spirit = TacticsScene.spirit(helper, at);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        return spirit;
    }

    private static Optional<DecisionRecord> ignoreToFight(SpiritEntity spirit) {
        List<DecisionRecord> records = spirit.tactics().decisions(LIMIT);
        for (DecisionRecord record : records) {
            if (record.changed().equals(DecisionSnapshot.DECISION) && record.before().equals(TacticalOption.IGNORE.name())
                    && record.after().equals(TacticalOption.FIGHT.name())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    @GameTest(template = "arena", batch = "tactics_journal", timeoutTicks = 400)
    public static void journal_HuskAttacksAfterCalm_RecordsIgnoreToFightWithReason(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos at = new BlockPos(5, 1, 5);
        SpiritEntity spirit = swordsman(helper, at);
        BlockPos huskAt = new BlockPos(5, 1, 5 + HUSK_BLOCKS);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, FULL_HP);

        helper.succeedWhen(() -> {
            scene.assertStarted();
            Optional<DecisionRecord> record = ignoreToFight(spirit);
            helper.assertTrue(record.isPresent(),
                    "Журнал обязан содержать запись «игнор → бой»: без атаки оборонительный дух игнорировал хаска, "
                            + "с атакой дерется. Записи: " + spirit.tactics().decisions(LIMIT));
            DecisionRecord entry = record.get();
            helper.assertTrue(!entry.rule().isBlank(),
                    "Запись называет причину — сработавшее правило или оценку: " + entry);
            helper.assertTrue(!entry.byScore() || !entry.factors().isEmpty(),
                    "Решение по оценке обязано назвать факторы с весами: " + DecisionText.format(entry));
            for (ReasonFactor factor : entry.factors()) {
                helper.assertTrue(factor.weight() > 0,
                        "В записи только факторы, перевесившие соперника: " + factor);
            }
            helper.assertTrue(entry.facts().enemies() >= 1,
                    "В фактах записи враг рядом, получено " + entry.facts().enemies());
            helper.assertTrue(entry.facts().nearest().startsWith(HUSK_TYPE),
                    "Ближайший враг назван типом, получено " + entry.facts().nearest());
            helper.assertTrue(entry.facts().hp() == FULL_HP && entry.facts().maxHp() == FULL_HP,
                    "Здоровье духа в фактах записи: " + entry.facts().hp() + "/" + entry.facts().maxHp());
            helper.assertTrue(DecisionText.format(entry).startsWith(DecisionText.LOG_PREFIX),
                    "Строка записи несёт префикс " + DecisionText.LOG_PREFIX + " для лога сервера: "
                            + DecisionText.format(entry));
            helper.assertTrue(scene.ticks() >= OBSERVED_TICKS,
                    "бой наблюдается ещё " + (OBSERVED_TICKS - scene.ticks()) + " тиков из " + OBSERVED_TICKS
                            + ": журнал мог получить записи позже");
            TacticsScene.finish(helper, spirit);
        });
    }
}
