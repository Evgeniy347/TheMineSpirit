package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.town.SettlementReach;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CraftTerminalChannel {
    public static final CustomPacketPayload.Type<Action> ACTION =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "craft_action"));
    public static final CustomPacketPayload.Type<Snapshot> SNAPSHOT =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "craft_snapshot"));
    public static final CustomPacketPayload.Type<ChainReply> CHAIN =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "craft_chain"));

    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_LINES = 64;
    private static final int MAX_TEXT = 256;
    private static final List<Kind> KINDS = List.of(Kind.values());
    private static final List<SettlementReach.Verdict> VERDICTS = List.of(SettlementReach.Verdict.values());
    private static volatile Consumer<Snapshot> snapshotReceiver = snapshot -> {};
    private static volatile Consumer<ChainReply> chainReceiver = reply -> {};

    private CraftTerminalChannel() {}

    public enum Kind {
        OPEN,
        REFRESH,
        TAKE,
        PUT,
        FILL,
        CLEAR,
        CHAIN_PREVIEW,
        CHAIN_CRAFT,
        ORDER,
        CANCEL
    }

    public record Action(Kind kind, ItemStack stack, int amount, boolean flag, String text)
            implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC =
                StreamCodec.of(CraftTerminalChannel::writeAction, CraftTerminalChannel::readAction);

        public Action {
            Objects.requireNonNull(kind, "Вид действия вкладки «Крафт» не может быть null");
            stack = stack.copy();
            if (text.length() > MAX_TEXT) {
                throw new IllegalArgumentException("Текст действия длиннее " + MAX_TEXT + " символов: пакет повреждён");
            }
        }

        public static Action of(Kind kind) {
            return new Action(kind, ItemStack.EMPTY, 0, false, "");
        }

        @Override
        public CustomPacketPayload.Type<Action> type() {
            return ACTION;
        }
    }

    public record Entry(ItemStack sample, int count, int free) {
        public Entry {
            sample = sample.copyWithCount(1);
        }
    }

    public record Snapshot(SettlementReach.Verdict verdict, int distance, List<Entry> entries, List<Order> orders)
            implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC =
                StreamCodec.of(CraftTerminalChannel::writeSnapshot, CraftTerminalChannel::readSnapshot);

        public Snapshot {
            Objects.requireNonNull(verdict, "Доступ к вкладке не может быть null");
            entries = List.copyOf(entries);
            orders = List.copyOf(orders);
        }

        @Override
        public CustomPacketPayload.Type<Snapshot> type() {
            return SNAPSHOT;
        }
    }

    public record Order(int id, ItemStack target, int count, String status, List<Line> missing) {
        public Order {
            target = target.copyWithCount(1);
            missing = List.copyOf(missing);
        }
    }

    public record Line(ItemStack sample, int count) {
        public Line {
            sample = sample.copyWithCount(1);
        }
    }

    public record ChainReply(
            String recipe,
            ItemStack target,
            int crafts,
            int maxCrafts,
            int steps,
            List<Line> consumed,
            List<Line> leftovers,
            List<Line> missing,
            Component notice)
            implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, ChainReply> STREAM_CODEC =
                StreamCodec.of(CraftTerminalChannel::writeChain, CraftTerminalChannel::readChain);

        public ChainReply {
            target = target.copy();
            consumed = List.copyOf(consumed);
            leftovers = List.copyOf(leftovers);
            missing = List.copyOf(missing);
        }

        public boolean feasible() {
            return missing.isEmpty() && crafts > 0;
        }

        @Override
        public CustomPacketPayload.Type<ChainReply> type() {
            return CHAIN;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CraftTerminalChannel::registerPayloads);
    }

    public static void onClientSnapshot(Consumer<Snapshot> receiver) {
        snapshotReceiver = receiver;
    }

    public static void onClientChain(Consumer<ChainReply> receiver) {
        chainReceiver = receiver;
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(ACTION, Action.STREAM_CODEC, CraftTerminalChannel::handleAction);
        registrar.playToClient(
                SNAPSHOT, Snapshot.STREAM_CODEC, (snapshot, context) -> snapshotReceiver.accept(snapshot));
        registrar.playToClient(CHAIN, ChainReply.STREAM_CODEC, (reply, context) -> chainReceiver.accept(reply));
    }

    private static void handleAction(Action action, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CraftTerminalService.handle(player, action);
        }
    }

    private static void writeAction(RegistryFriendlyByteBuf buf, Action action) {
        buf.writeVarInt(KINDS.indexOf(action.kind()));
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, action.stack());
        buf.writeVarInt(action.amount());
        buf.writeBoolean(action.flag());
        buf.writeUtf(action.text(), MAX_TEXT);
    }

    private static Action readAction(RegistryFriendlyByteBuf buf) {
        Kind kind = KINDS.get(checked(buf.readVarInt(), KINDS.size() - 1, "вид действия"));
        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int amount = buf.readVarInt();
        boolean flag = buf.readBoolean();
        return new Action(kind, stack, amount, flag, buf.readUtf(MAX_TEXT));
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buf, Snapshot snapshot) {
        buf.writeVarInt(VERDICTS.indexOf(snapshot.verdict()));
        buf.writeVarInt(snapshot.distance());
        List<Entry> entries =
                snapshot.entries().subList(0, Math.min(snapshot.entries().size(), MAX_ENTRIES));
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            ItemStack.STREAM_CODEC.encode(buf, entry.sample());
            buf.writeVarInt(entry.count());
            buf.writeVarInt(entry.free());
        }
        List<Order> orders =
                snapshot.orders().subList(0, Math.min(snapshot.orders().size(), MAX_LINES));
        buf.writeVarInt(orders.size());
        for (Order order : orders) {
            buf.writeVarInt(order.id());
            ItemStack.STREAM_CODEC.encode(buf, order.target());
            buf.writeVarInt(order.count());
            buf.writeUtf(order.status(), MAX_TEXT);
            writeLines(buf, order.missing());
        }
    }

    private static Snapshot readSnapshot(RegistryFriendlyByteBuf buf) {
        SettlementReach.Verdict verdict = VERDICTS.get(checked(buf.readVarInt(), VERDICTS.size() - 1, "доступ"));
        int distance = buf.readVarInt();
        int count = checked(buf.readVarInt(), MAX_ENTRIES, "вещей поселения");
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));
        }
        int orderCount = checked(buf.readVarInt(), MAX_LINES, "заказов");
        List<Order> orders = new ArrayList<>(orderCount);
        for (int index = 0; index < orderCount; index++) {
            int id = buf.readVarInt();
            ItemStack target = ItemStack.STREAM_CODEC.decode(buf);
            int amount = buf.readVarInt();
            String status = buf.readUtf(MAX_TEXT);
            orders.add(new Order(id, target, amount, status, readLines(buf)));
        }
        return new Snapshot(verdict, distance, entries, orders);
    }

    private static void writeChain(RegistryFriendlyByteBuf buf, ChainReply reply) {
        buf.writeUtf(reply.recipe(), MAX_TEXT);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, reply.target());
        buf.writeVarInt(reply.crafts());
        buf.writeVarInt(reply.maxCrafts());
        buf.writeVarInt(reply.steps());
        writeLines(buf, reply.consumed());
        writeLines(buf, reply.leftovers());
        writeLines(buf, reply.missing());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, reply.notice());
    }

    private static ChainReply readChain(RegistryFriendlyByteBuf buf) {
        String recipe = buf.readUtf(MAX_TEXT);
        ItemStack target = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int crafts = buf.readVarInt();
        int maxCrafts = buf.readVarInt();
        int steps = buf.readVarInt();
        List<Line> consumed = readLines(buf);
        List<Line> leftovers = readLines(buf);
        List<Line> missing = readLines(buf);
        return new ChainReply(
                recipe,
                target,
                crafts,
                maxCrafts,
                steps,
                consumed,
                leftovers,
                missing,
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
    }

    private static void writeLines(RegistryFriendlyByteBuf buf, List<Line> all) {
        List<Line> lines = all.subList(0, Math.min(all.size(), MAX_LINES));
        buf.writeVarInt(lines.size());
        for (Line line : lines) {
            ItemStack.STREAM_CODEC.encode(buf, line.sample());
            buf.writeVarInt(line.count());
        }
    }

    private static List<Line> readLines(RegistryFriendlyByteBuf buf) {
        int count = checked(buf.readVarInt(), MAX_LINES, "строк плана");
        List<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new Line(ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt()));
        }
        return lines;
    }

    private static int checked(int value, int max, String what) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Пакет вкладки «Крафт» несёт " + value + " (" + what + "), предел " + max
                    + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        return value;
    }
}
