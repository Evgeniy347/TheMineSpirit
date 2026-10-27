package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.board.JournalService;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardJournalGameTests {
    private static final String BATCH = "board_journal";

    private BoardJournalGameTests() {}

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 20)
    public static void journal_BoardJournal_InWorldFolderNotConfig(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path journal = JournalService.inWorld(server).path().toAbsolutePath().normalize();
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        helper.assertTrue(
                journal.startsWith(world) && !journal.startsWith(config),
                "Журнал доски " + journal + " обязан лежать в папке мира " + world + ", а не в " + config);
        helper.succeed();
    }
}
