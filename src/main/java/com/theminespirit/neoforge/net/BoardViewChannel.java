package com.theminespirit.neoforge.net;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.rest.BoardTask;
import com.theminespirit.core.rest.BoardTasks;
import com.theminespirit.core.ui.BoardSnapshot;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

/**
 * Снимок доски для экрана игрока: клиент шлёт запрос при открытии экрана, сервер отвечает плитками. Живого
 * обновления нет. Формат пакета не версионируется отдельно: сборки клиента и сервера уже сверены
 * {@link ModProtocolHandshake}, поэтому обе стороны читают один и тот же кодек.
 */
public final class BoardViewChannel {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "board_view_request"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "board_view_reply"));

    private static final String PROTOCOL_VERSION = "1";
    private static final int JOURNAL_LOOKBACK = 1024;
    private static final int MAX_TILES = 4096;
    private static final List<BoardTileGroup> GROUPS = List.of(BoardTileGroup.values());
    private static volatile Consumer<List<BoardTile>> clientReceiver = tiles -> {};

    private BoardViewChannel() {}

    public record Request() implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(List<BoardTile> tiles) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.of(BoardViewChannel::write, BoardViewChannel::read);

        public Reply {
            tiles = List.copyOf(tiles);
        }

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(BoardViewChannel::registerPayloads);
    }

    public static void onClientReply(Consumer<List<BoardTile>> receiver) {
        clientReceiver = receiver;
    }

    public static List<BoardTile> snapshot(ServerLevel level) {
        Optional<BoardService> service = BoardRegistry.peek(level)
                .or(() -> BoardRegistry.peek(level.getServer().overworld()));
        if (service.isEmpty()) {
            return List.of();
        }
        Board board = service.get().board();
        List<BoardTask> tasks = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            tasks.add(BoardTasks.of(board, ticket, service.get().stock()));
        }
        Map<String, Long> doneTicks = new HashMap<>();
        for (BoardLogEntry entry : service.get().journal().last(JOURNAL_LOOKBACK)) {
            if (entry.event() == BoardLog.Event.DONE) {
                doneTicks.put(entry.ticketId(), entry.tick());
            }
        }
        MinecraftServer server = level.getServer();
        return BoardSnapshot.build(tasks, doneTicks, uuid -> spiritName(server, uuid));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(REQUEST, Request.STREAM_CODEC, BoardViewChannel::handleRequest);
        registrar.playToClient(REPLY, Reply.STREAM_CODEC, (reply, context) -> clientReceiver.accept(reply.tiles()));
    }

    private static void handleRequest(Request request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.reply(new Reply(snapshot(player.serverLevel())));
        }
    }

    private static @Nullable String spiritName(MinecraftServer server, String uuid) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof SpiritEntity spirit) {
                return spirit.displayName();
            }
        }
        return null;
    }

    private static void write(FriendlyByteBuf buf, Reply reply) {
        List<BoardTile> tiles = reply.tiles().subList(0, Math.min(reply.tiles().size(), MAX_TILES));
        buf.writeVarInt(tiles.size());
        for (BoardTile tile : tiles) {
            buf.writeUtf(tile.id());
            buf.writeUtf(tile.boardCase());
            buf.writeVarInt(tile.level());
            buf.writeVarInt(tile.priority());
            buf.writeBoolean(tile.spirit() != null);
            if (tile.spirit() != null) {
                buf.writeUtf(tile.spirit());
            }
            buf.writeUtf(tile.status());
            buf.writeVarInt(GROUPS.indexOf(tile.group()));
            buf.writeVarInt(tile.missing().size());
            for (Map.Entry<String, Integer> item : tile.missing().entrySet()) {
                buf.writeUtf(item.getKey());
                buf.writeVarInt(item.getValue());
            }
            buf.writeVarLong(tile.doneAt());
        }
    }

    private static Reply read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TILES) {
            throw new IllegalArgumentException("Снимок доски несёт " + count + " плиток, предел " + MAX_TILES
                    + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        List<BoardTile> tiles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            String boardCase = buf.readUtf();
            int level = buf.readVarInt();
            int priority = buf.readVarInt();
            String spirit = buf.readBoolean() ? buf.readUtf() : null;
            String status = buf.readUtf();
            BoardTileGroup group = GROUPS.get(buf.readVarInt());
            int missingCount = buf.readVarInt();
            Map<String, Integer> missing = new LinkedHashMap<>();
            for (int j = 0; j < missingCount; j++) {
                missing.put(buf.readUtf(), buf.readVarInt());
            }
            long doneAt = buf.readVarLong();
            tiles.add(new BoardTile(id, boardCase, level, priority, spirit, status, group, missing, doneAt));
        }
        return new Reply(tiles);
    }
}
