package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.DecisionSnapshot;
import com.theminespirit.core.tactics.adapter.DecisionText;
import com.theminespirit.core.tactics.adapter.ReasonFactor;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
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
    private static final int EMPTY_JOURNAL_TICK = 20;

    private TacticsJournalGameTests() {}

    private static SpiritEntity swordsman(GameTestHelper helper, BlockPos at, String tag) {
        SpiritEntity spirit = TacticsScene.spirit(helper, at);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.addTag(tag);
        return spirit;
    }

    private static String freshTag(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    private static String tagSelector(String tag) {
        return "@e[type=" + ModIdentity.MOD_ID + ":spirit,tag=" + tag + "]";
    }

    private static Optional<DecisionRecord> ignoreToFight(SpiritEntity spirit) {
        List<DecisionRecord> records = spirit.tactics().decisions(LIMIT);
        for (DecisionRecord record : records) {
            if (record.changed().equals(DecisionSnapshot.DECISION)
                    && record.before().equals(TacticalOption.IGNORE.name())
                    && record.after().equals(TacticalOption.FIGHT.name())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    // Оператор читает журнал в чате: без перехвата сообщений команда не проверяет, что именно ушло игроку.
    private static CommandSourceStack capturingSource(GameTestHelper helper, List<String> chat) {
        CommandSourceStack base = helper.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withPosition(helper.absoluteVec(new Vec3(5, 1, 5)))
                .withPermission(4);
        return base.withSource(new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                chat.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return base.source.acceptsSuccess();
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        });
    }

    private static int execute(
            CommandDispatcher<CommandSourceStack> dispatcher, String command, CommandSourceStack source) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Команда '" + command + "' не разобрана: " + e.getMessage(), e);
        }
    }

    @GameTest(template = "arena", batch = "tactics_journal", timeoutTicks = 400)
    public static void journal_HuskAttacksAfterCalm_RecordsIgnoreToFightWithReason(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos at = new BlockPos(5, 1, 5);
        SpiritEntity spirit = swordsman(helper, at, freshTag("journal"));
        BlockPos huskAt = new BlockPos(5, 1, 5 + HUSK_BLOCKS);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, FULL_HP);

        helper.succeedWhen(() -> {
            scene.assertStarted();
            Optional<DecisionRecord> record = ignoreToFight(spirit);
            helper.assertTrue(
                    record.isPresent(),
                    "Журнал обязан содержать запись «игнор → бой»: без атаки оборонительный дух игнорировал хаска, "
                            + "с атакой дерется. Записи: " + spirit.tactics().decisions(LIMIT));
            DecisionRecord entry = record.get();
            helper.assertTrue(
                    !entry.rule().isBlank(), "Запись называет причину — сработавшее правило или оценку: " + entry);
            helper.assertTrue(
                    !entry.byScore() || !entry.factors().isEmpty(),
                    "Решение по оценке обязано назвать факторы с весами: " + DecisionText.format(entry));
            for (ReasonFactor factor : entry.factors()) {
                helper.assertTrue(factor.weight() > 0, "В записи только факторы, перевесившие соперника: " + factor);
            }
            helper.assertTrue(
                    entry.facts().enemies() >= 1,
                    "В фактах записи враг рядом, получено " + entry.facts().enemies());
            helper.assertTrue(
                    entry.facts().nearest().startsWith(HUSK_TYPE),
                    "Ближайший враг назван типом, получено " + entry.facts().nearest());
            helper.assertTrue(
                    entry.facts().hp() == FULL_HP && entry.facts().maxHp() == FULL_HP,
                    "Здоровье духа в фактах записи: " + entry.facts().hp() + "/"
                            + entry.facts().maxHp());
            helper.assertTrue(
                    DecisionText.format(entry).startsWith(DecisionText.LOG_PREFIX),
                    "Строка записи несёт префикс " + DecisionText.LOG_PREFIX + " для лога сервера: "
                            + DecisionText.format(entry));
            helper.assertTrue(
                    scene.ticks() >= OBSERVED_TICKS,
                    "бой наблюдается ещё " + (OBSERVED_TICKS - scene.ticks()) + " тиков из " + OBSERVED_TICKS
                            + ": журнал мог получить записи позже");
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_journal_command", timeoutTicks = 400)
    public static void decisions_EmptyThenFilledJournal_CommandPrintsLastRecordsInChat(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        String tag = freshTag("decisions");
        SpiritEntity spirit = swordsman(helper, new BlockPos(5, 1, 5), tag);
        BlockPos huskAt = new BlockPos(5, 1, 5 + HUSK_BLOCKS);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, FULL_HP);
        List<String> chat = new ArrayList<>();
        CommandSourceStack source = capturingSource(helper, chat);
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        AtomicInteger emptyResult = new AtomicInteger(-1);

        helper.runAtTickTime(
                EMPTY_JOURNAL_TICK,
                () -> emptyResult.set(execute(dispatcher, "spirit decisions " + tagSelector(tag), source)));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    scene.started(),
                    "Сцена атаки не запустилась: дух на тике " + spirit.tickCount + " (жив " + spirit.isAlive()
                            + ", здоровье " + spirit.getHealth() + "), тиков сцены " + scene.ticks());
            helper.assertTrue(
                    emptyResult.get() == 1,
                    "До атаки хаска журнал пуст: команда обязана сработать на тике " + EMPTY_JOURNAL_TICK
                            + " и вернуть 1, получено " + emptyResult.get());
            helper.assertTrue(
                    chat.size() == 1 && chat.get(0).contains("ещё не менял решение"),
                    "Пустой журнал объясняется текстом, а не молчанием: " + chat);
            Optional<DecisionRecord> record = ignoreToFight(spirit);
            helper.assertTrue(
                    record.isPresent(),
                    "Журнал обязан получить запись «игнор → бой» после атаки хаска: "
                            + spirit.tactics().decisions(LIMIT));
            chat.clear();
            int printed = execute(dispatcher, "spirit decisions " + tagSelector(tag), source);

            helper.assertTrue(printed == 1, "Команда возвращает число духов, которым показала журнал: " + printed);
            helper.assertTrue(!chat.isEmpty(), "Команда печатает записи журнала в чат: " + chat);
            helper.assertTrue(
                    chat.size() <= TacticsService.DECISIONS_IN_CHAT,
                    "В чат уходит не больше " + TacticsService.DECISIONS_IN_CHAT + " записей, получено " + chat.size());
            String joined = String.join("\n", chat);
            helper.assertTrue(
                    chat.get(0).startsWith(DecisionText.LOG_PREFIX),
                    "Строка записи несёт префикс " + DecisionText.LOG_PREFIX + ": " + chat.get(0));
            helper.assertTrue(
                    joined.contains("решение IGNORE → FIGHT"), "В чате видно, что было и что стало: " + joined);
            helper.assertTrue(
                    joined.contains(spirit.displayName()) && joined.contains(spirit.getStringUUID()),
                    "Запись называет духа именем и UUID, чтобы чат сервера не смешивал духов: " + joined);
            helper.assertTrue(
                    joined.contains("врагов рядом") && joined.contains("здоровье"),
                    "Ключевые факты идут в чат: без них решение не разобрать: " + joined);
            TacticsScene.finish(helper, spirit);
        });
    }
}
