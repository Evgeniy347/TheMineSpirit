package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.WoolWatch;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WoolBedsGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final Set<BoardCase> WOOL_CASES = Set.of(
            BoardCase.CRAFT_SHEARS,
            BoardCase.SHEAR_SHEEP,
            BoardCase.HUNT_WILD_SHEEP,
            BoardCase.WEAVE_WOOL,
            BoardCase.CRAFT_BED);

    private WoolBedsGameTests() {}

    private static void completePen(BoardService service, SpiritEntity spirit, BlockPos home, long now) {
        if (service.board().statusOf("sheep_pen").orElse(null) == BoardStatus.DONE) {
            return;
        }
        service.submit(
                new BoardTicket(
                        "sheep_pen",
                        BoardCase.BUILD_PEN,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(BoardCase.BUILD_PEN),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(new BlockCoord(home.getX(), home.getY(), home.getZ())));
        service.board().claim("sheep_pen", spirit.getUUID(), now);
        service.board().complete("sheep_pen");
    }

    private static void closePen(
            BoardService service, SpiritEntity spirit, BlockPos home, long now, ServerLevel level) {
        service.submit(
                new BoardTicket(
                        "sheep_pen",
                        BoardCase.BUILD_PEN,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(BoardCase.BUILD_PEN),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(new BlockCoord(home.getX(), home.getY(), home.getZ())));
        service.cancel(level, "sheep_pen");
    }

    private static void capture(BoardService service, Sheep sheep) {
        BlockPos at = sheep.blockPosition();
        BlockCoord target = new BlockCoord(at.getX(), at.getY(), at.getZ());
        service.noteCapture(
                "test:sheep:" + sheep.getUUID(),
                Species.SHEEP,
                sheep.getUUID(),
                TicketTarget.at(target).withSpecies(Species.SHEEP));
    }

    private static boolean active(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == boardCase)
                .anyMatch(ticket -> service.board()
                        .statusOf(ticket.id())
                        .map(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .orElse(false));
    }

    private static int woolCaseCount(BoardService service) {
        return (int) service.board().all().stream()
                .filter(ticket -> WOOL_CASES.contains(ticket.boardCase()))
                .filter(ticket -> service.board()
                        .statusOf(ticket.id())
                        .map(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .orElse(false))
                .count();
    }

    private static void clearWoolTasks(BoardService service, ServerLevel level) {
        for (BoardTicket ticket : service.board().all()) {
            if (WOOL_CASES.contains(ticket.boardCase())) {
                service.cancel(level, ticket.id());
            }
        }
    }

    @GameTest(template = "arena", batch = "woolbeds_gate", timeoutTicks = 200)
    @SettlementSystems(board = true)
    public static void woolWatch_PenGate_OnlyAtFourSheep(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START);
        BlockPos home = helper.absolutePos(START);
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        ServerLevel level = helper.getLevel();
        BoardService service = BoardRegistry.get(level);
        long now = level.getGameTime();
        clearWoolTasks(service, level);
        completePen(service, spirit, home, now);
        List<Sheep> sheep = List.of(
                HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(3, 1, 3)),
                HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(4, 1, 3)),
                HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(5, 1, 3)),
                HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(3, 1, 4)));
        HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(8, 1, 8));
        for (int index = 0; index < 3; index++) {
            capture(service, sheep.get(index));
        }
        StockView stock =
                new StockView(Map.of("shears", 1, "iron_ingot", 2, "string", 8, "white_wool", 3, "oak_planks", 3));
        WoolWatch watch = new WoolWatch();
        watch.tick(level, service, List.of(spirit), stock, now);
        helper.assertTrue(!watch.live(), "три овцы — гейт шерсти закрыт: до него только разведение");
        helper.assertTrue(
                woolCaseCount(service) == 0,
                "при трёх овцах шерстяных заданий быть не должно, фактически " + woolCaseCount(service));

        capture(service, sheep.get(3));
        watch.tick(level, service, List.of(spirit), stock, now + 200);
        helper.assertTrue(watch.live(), "четыре овцы — гейт шерсти открыт");
        helper.assertTrue(active(service, BoardCase.SHEAR_SHEEP), "при четырёх овцах обязана появиться стрижка загона");
        helper.assertTrue(active(service, BoardCase.WEAVE_WOOL), "нитей восемь — обязана появиться шерсть из нитей");
        helper.assertTrue(active(service, BoardCase.CRAFT_BED), "три шерсти и три доски — обязана появиться кровать");
        helper.assertTrue(!active(service, BoardCase.CRAFT_SHEARS), "ножницы уже есть — второй раз их не куют");
        helper.assertTrue(
                !active(service, BoardCase.HUNT_WILD_SHEEP),
                "один дух наружу не выходит: охота на диких овец ждёт группы");

        SpiritEntity partner = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 2));
        watch.tick(level, service, List.of(spirit, partner), stock, now + 400);
        helper.assertTrue(active(service, BoardCase.HUNT_WILD_SHEEP), "два духа — охота на диких овец выходит группой");
        clearWoolTasks(service, level);
        closePen(service, spirit, home, now + 600, level);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "woolbeds_shear", timeoutTicks = 400)
    public static void shear_SheepInPen_ShearedAlive(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        Sheep sheep = HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(3, 1, 3));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.SHEARS));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("shear", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "овца в загоне обязана постричься");
            helper.assertTrue(sheep.isAlive(), "стрижка не убивает: овца загона обязана остаться жива");
            helper.assertTrue(sheep.isSheared(), "овца обязана остаться без шерсти: иначе шерсть взялась из воздуха");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL) >= 1,
                    "шерсть обязана попасть в инвентарь, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "woolbeds_hunt", timeoutTicks = 400)
    public static void hunt_PenSheepAlive_WildSheepCulled(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Sheep own = HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(3, 1, 1));
        HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(8, 1, 8));
        HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(8, 1, 6));
        HusbandryGameTests.spawnStill(helper, EntityType.SHEEP, new BlockPos(6, 1, 8));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.IRON_SWORD));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("hunt", Map.of("species", "sheep", "radius", 16, "count", 3)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "три дикие овцы обязаны забиться");
            List<Sheep> sheep = HusbandryGameTests.alive(helper, Sheep.class);
            helper.assertTrue(
                    sheep.size() == 1 && sheep.get(0).equals(own),
                    "жива обязана остаться ровно своя овца домашнего круга, фактически живых " + sheep.size()
                            + ", своя жива: " + own.isAlive());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL) >= 1,
                    "шерсть с диких овец обязана подобраться");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.MUTTON) >= 1,
                    "баранина с диких овец обязана подобраться");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "woolbeds_string", timeoutTicks = 400)
    public static void weave_StringEight_OneWoolFourLeft(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.STRING, 8));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("craft", Map.of("item", "minecraft:white_wool", "count", 1, "radius", 16)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "из восьми нитей обязана выйти шерсть");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL) == 1,
                    "одна шерсть обязана появиться в инвентаре, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.STRING) == 4,
                    "четыре нити обязаны остаться запасом, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.STRING));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "woolbeds_bed", timeoutTicks = 400)
    public static void craft_ThreeWoolThreePlanks_BedInInventory(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(
                helper, START, new ItemStack(Items.WHITE_WOOL, 3), new ItemStack(Items.OAK_PLANKS, 3));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("craft", Map.of("item", "minecraft:white_bed", "count", 1, "radius", 16)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "из трёх шерсти и трёх досок обязана выйти кровать");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_BED) == 1,
                    "белая кровать обязана появиться в инвентаре, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_BED));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL) == 0,
                    "три шерсти обязаны уйти в кровать");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_PLANKS) == 0,
                    "три доски обязаны уйти в кровать");
            GameTestFixtures.finish(helper);
        });
    }
}
