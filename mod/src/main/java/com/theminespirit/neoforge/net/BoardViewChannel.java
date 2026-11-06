package com.theminespirit.neoforge.net;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTiming;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.rest.BoardTask;
import com.theminespirit.core.rest.BoardTasks;
import com.theminespirit.core.ui.BoardFilter;
import com.theminespirit.core.ui.BoardPage;
import com.theminespirit.core.ui.BoardPageQuery;
import com.theminespirit.core.ui.BoardPaging;
import com.theminespirit.core.ui.BoardSnapshot;
import com.theminespirit.core.ui.BoardStage;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.core.ui.BoardVersion;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
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

public final class BoardViewChannel {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "board_view_request"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "board_view_reply"));

    private static final String PROTOCOL_VERSION = "5";
    private static final List<BoardTileGroup> GROUPS = List.of(BoardTileGroup.values());
    private static final List<BoardFilter.Spirit> SPIRITS = List.of(BoardFilter.Spirit.values());
    private static volatile Consumer<Reply> clientReceiver = reply -> {};

    private BoardViewChannel() {}

    public record Request(BoardPageQuery query) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.of(BoardViewChannel::writeRequest, BoardViewChannel::readRequest);

        public Request {
            Objects.requireNonNull(query, "Запрос доски не может быть null: без него нечего отдавать");
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(BoardPage page, BoardStage stage) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.of(BoardViewChannel::write, BoardViewChannel::read);

        public Reply {
            Objects.requireNonNull(page, "Порция доски не может быть null: ответ без неё пуст");
            Objects.requireNonNull(stage, "Этап не может быть null: до первого дома — BoardStage.FIRST");
        }

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(BoardViewChannel::registerPayloads);
    }

    public static void onClientReply(Consumer<Reply> receiver) {
        clientReceiver = Objects.requireNonNull(receiver, "Получатель ответа доски не может быть null");
    }

    public static BoardStage stage(ServerLevel level) {
        return BoardRegistry.peek(level)
                .or(() -> BoardRegistry.peek(level.getServer().overworld()))
                .map(service -> new BoardStage(service.board().ledger().stage()))
                .orElse(BoardStage.FIRST);
    }

    public static BoardPage snapshot(ServerLevel level, BoardPageQuery query) {
        Objects.requireNonNull(query, "query не может быть null: снимок собирают под запрос порции");
        Optional<BoardService> service = BoardRegistry.peek(level)
                .or(() -> BoardRegistry.peek(level.getServer().overworld()));
        if (service.isEmpty()) {
            return BoardPaging.page(List.of(), query, 0L);
        }
        BoardService found = service.get();
        Board board = found.board();
        Map<String, BoardTiming> timings =
                BoardTiming.fromJournal(found.journal().last(BoardTiming.JOURNAL_LOOKBACK));
        List<BoardTask> tasks = new ArrayList<>();
        Map<String, List<String>> targetDetails = new LinkedHashMap<>();
        for (BoardTicket ticket : board.all()) {
            TicketTarget target = found.targetOf(ticket.id(), List.of());
            targetDetails.put(ticket.id(), target.details());
            tasks.add(BoardTasks.of(
                    board,
                    ticket,
                    board.statusOf(ticket.id()).orElse(BoardStatus.PENDING),
                    found.stock(),
                    timings.getOrDefault(ticket.id(), BoardTiming.NONE),
                    target));
        }
        Function<String, @Nullable String> names = spiritNames(level.getServer());
        return BoardPaging.pageRows(
                BoardSnapshot.rows(tasks), query, names, targetDetails, BoardVersion.ofTasks(tasks));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(REQUEST, Request.STREAM_CODEC, BoardViewChannel::handleRequest);
        registrar.playToClient(REPLY, Reply.STREAM_CODEC, (reply, context) -> clientReceiver.accept(reply));
    }

    private static void handleRequest(Request request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.reply(new Reply(snapshot(player.serverLevel(), request.query()), stage(player.serverLevel())));
        }
    }

    private static Function<String, @Nullable String> spiritNames(MinecraftServer server) {
        Map<String, String> cache = new HashMap<>();
        return uuid -> {
            if (cache.containsKey(uuid)) {
                return cache.get(uuid);
            }
            String name = spiritName(server, uuid);
            cache.put(uuid, name);
            return name;
        };
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

    private static void writeRequest(FriendlyByteBuf buf, Request request) {
        BoardPageQuery query = request.query();
        buf.writeVarInt(GROUPS.indexOf(query.group()));
        buf.writeVarInt(query.offset());
        buf.writeVarInt(query.limit());
        buf.writeVarInt(query.status() == null ? -1 : GROUPS.indexOf(query.status()));
        buf.writeVarInt(SPIRITS.indexOf(query.spirit()));
        buf.writeBoolean(query.spiritName() != null);
        if (query.spiritName() != null) {
            buf.writeUtf(query.spiritName());
        }
    }

    private static Request readRequest(FriendlyByteBuf buf) {
        BoardTileGroup group = GROUPS.get(buf.readVarInt());
        int offset = buf.readVarInt();
        int limit = buf.readVarInt();
        int statusIndex = buf.readVarInt();
        BoardTileGroup status = statusIndex < 0 ? null : GROUPS.get(statusIndex);
        BoardFilter.Spirit spirit = SPIRITS.get(buf.readVarInt());
        String spiritName = buf.readBoolean() ? buf.readUtf() : null;
        return new Request(new BoardPageQuery(group, offset, limit, status, spirit, spiritName));
    }

    private static void write(FriendlyByteBuf buf, Reply reply) {
        BoardPage page = reply.page();
        buf.writeVarInt(GROUPS.indexOf(page.group()));
        buf.writeVarInt(page.offset());
        for (BoardTileGroup group : GROUPS) {
            buf.writeVarInt(page.totals().getOrDefault(group, 0));
        }
        buf.writeVarLong(page.version());
        buf.writeBoolean(page.hasMore());
        buf.writeVarInt(page.spirits().size());
        for (String spirit : page.spirits()) {
            buf.writeUtf(spirit);
        }
        BoardTileCodec.write(buf, page.tiles());
        buf.writeVarInt(reply.stage().number());
    }

    private static Reply read(FriendlyByteBuf buf) {
        BoardTileGroup group = GROUPS.get(buf.readVarInt());
        int offset = buf.readVarInt();
        Map<BoardTileGroup, Integer> totals = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup value : GROUPS) {
            totals.put(value, buf.readVarInt());
        }
        long version = buf.readVarLong();
        boolean hasMore = buf.readBoolean();
        int spiritCount = buf.readVarInt();
        List<String> spirits = new ArrayList<>(spiritCount);
        for (int index = 0; index < spiritCount; index++) {
            spirits.add(buf.readUtf());
        }
        BoardPage page = new BoardPage(group, offset, BoardTileCodec.read(buf), totals, spirits, version, hasMore);
        return new Reply(page, new BoardStage(buf.readVarInt()));
    }
}
