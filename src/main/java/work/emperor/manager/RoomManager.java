package work.emperor.manager;

import work.emperor.model.PlayerContext;
import work.emperor.model.Status;
import work.emperor.util.ServerLogger;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RoomManager {
    private final ServerLogger logger;
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Queue<PlayerContext> waiting = new ConcurrentLinkedQueue<>();
    private final AtomicInteger roomCounter = new AtomicInteger(1);
    private volatile Supplier<Deck.DeckConfig> deckSupplier = Deck.DeckConfig::standard;

    public RoomManager(ServerLogger logger) {
        this.logger = logger;
    }

    public void setDeckSupplier(Supplier<Deck.DeckConfig> supplier) {
        if (supplier != null) this.deckSupplier = supplier;
    }

    public synchronized GameRoom match(PlayerContext ctx) {
        PlayerContext opponent;
        while ((opponent = waiting.poll()) != null) {
            if (opponent == ctx) continue;
            if (!opponent.isConnected() || opponent.status != Status.MATCHING) continue;
            if (opponent.accountId != null && ctx.accountId != null
                    && opponent.accountId.equals(ctx.accountId)) {
                continue;
            }
            return createRoom(opponent, ctx);
        }
        waiting.remove(ctx);
        waiting.offer(ctx);
        ctx.status = Status.MATCHING;
        logger.info("匹配入队 | session=" + ctx.sessionId + " | nick=" + ctx.nickname);
        return null;
    }

    public GameRoom getRoom(String roomId) {
        if (roomId == null) return null;
        return rooms.get(roomId);
    }

    public synchronized boolean cancelMatch(PlayerContext ctx) {
        boolean removed = waiting.remove(ctx);
        ctx.status = Status.IDLE;
        if (removed) {
            logger.info("取消匹配 | session=" + ctx.sessionId);
        }
        return removed;
    }

    public synchronized void abortMatch(PlayerContext ctx) {
        cancelMatch(ctx);
        removeFromQueue(ctx);
    }

    public synchronized PlayerContext leaveRoom(PlayerContext ctx) {
        GameRoom room = ctx.room;
        if (room == null) return null;
        rooms.remove(room.getId());
        PlayerContext opponent = room.opponentOf(ctx);
        ctx.resetRoom();
        if (opponent != null) opponent.resetRoom();
        logger.info("离开房间 | room=" + room.getId() + " | session=" + ctx.sessionId);
        return opponent;
    }

    public synchronized void retireRoom(GameRoom room) {
        if (room == null) return;
        rooms.remove(room.getId());
        room.a.resetRoom();
        room.b.resetRoom();
        logger.info("房间清理 | room=" + room.getId());
    }

    public synchronized void removeFromQueue(PlayerContext ctx) {
        waiting.remove(ctx);
    }

    public synchronized GameRoom createRoom(PlayerContext first, PlayerContext second) {
        String id = "R" + roomCounter.getAndIncrement();
        GameRoom room = new GameRoom(id, first, second, deckSupplier.get());
        rooms.put(id, room);
        first.room = room;
        second.room = room;
        first.status = Status.IN_ROOM;
        second.status = Status.IN_ROOM;
        logger.info(String.format("创建房间 | room=%s | A=%s(%s) | B=%s(%s)",
                id, first.sessionId, first.nickname, second.sessionId, second.nickname));
        return room;
    }
}
