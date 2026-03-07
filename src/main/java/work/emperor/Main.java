package work.emperor;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import work.emperor.manager.*;
import work.emperor.model.PlayerContext;
import work.emperor.model.Status;
import work.emperor.util.ServerLogger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Main {
    private static final Gson GSON = new Gson();
    private static final ServerLogger LOG = ServerLogger.get(Main.class);
    private static volatile EmperorServer ACTIVE_SERVER;
    private static final AuthService AUTH = new AuthService();
    private static final int MAX_NICK_LEN = 32;
    private static final int MAX_PASS_LEN = 64;
    private static final int MAX_SESSION_LEN = 32;
    private static final String DEFAULT_DECK_MODE = "standard";

    public static void main(String[] args) throws Exception {
        int wsPort = resolveWebSocketPort(args);
        int httpPort = resolveHttpPort(wsPort);

        EmperorServer server = new EmperorServer(wsPort);
        server.applyDeckMode(resolveDeckMode());
        EmperorServer secureServer = maybeStartSecureServer(wsPort);
        if (secureServer != null) {
            secureServer.applyDeckMode(resolveDeckMode());
        }
        ACTIVE_SERVER = server;
        server.start();

        HttpServer httpServer = StaticHttp.start(httpPort);
        startConsole(server);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop(1000);
            } catch (Exception ignored) {
            }
            if (secureServer != null) {
                try {
                    secureServer.stop(1000);
                } catch (Exception ignored) {
                }
            }
            if (httpServer != null) httpServer.stop(0);
        }));

        LOG.info(String.format("WebSocket server ready at ws://localhost:%d", wsPort));
        if (secureServer != null) {
            LOG.info(String.format("Secure WebSocket server ready at wss://localhost:%d", secureServer.getPort()));
        } else {
            LOG.info("Secure WebSocket (wss) not started: no keystore configured");
        }
        LOG.info(String.format("Static page served at http://localhost:%d/ (default WebSocket port %d)", httpPort, wsPort));
    }

    private static void startConsole(EmperorServer server) {
        Thread t = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue;
                    if (line.equalsIgnoreCase("/help")) {
                        printHelp();
                        continue;
                    }
                    if (line.startsWith("/set")) {
                        handleSetCommand(server, line);
                        continue;
                    }
                    if (line.startsWith("/say")) {
                        String msg = line.length() > 4 ? line.substring(4).trim() : "";
                        if (msg.isEmpty()) continue;
                        server.broadcastNotice("公告", msg, "info");
                        LOG.info("Broadcast /say: " + msg);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Console command thread stopped: " + e.getMessage());
            }
        }, "console-commands");
        t.setDaemon(true);
        t.start();
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  /help                     显示帮助");
        System.out.println("  /say <text>               全服公告");
        System.out.println("  /set randomcard on|off    切换牌堆模式（random=1/4-7/1-2/1-2/1-2，standard=1/4/1/1/1）");
    }

    private static void handleSetCommand(EmperorServer server, String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            System.out.println("用法: /set randomcard on|off");
            return;
        }
        String key = parts[1].toLowerCase();
        String val = parts[2].toLowerCase();
        if (key.equals("randomcard") || key.equals("randomdeck")) {
            if (val.equals("on")) {
                server.applyDeckMode("random");
                server.broadcastNotice("系统", "牌堆切到随机模式(1E/4-7C/1-2S/1-2T/1-2M)", "info");
            } else if (val.equals("off")) {
                server.applyDeckMode("standard");
                server.broadcastNotice("系统", "牌堆切回标准模式(1E/4C/1S/1T/1M)", "info");
            } else {
                System.out.println("随机牌堆参数须为 on/off");
            }
            return;
        }
        System.out.println("未知设置项: " + key);
    }

    private static int resolveWebSocketPort(String[] args) {
        if (args != null && args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        String env = System.getenv("WS_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 13337;
    }

    private static int resolveHttpPort(int fallbackFromWs) {
        String env = System.getenv("HTTP_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallbackFromWs + 1;
    }

    private static String resolveDeckMode() {
        return firstNonBlank(System.getenv("DECK_MODE"), System.getProperty("deck.mode"), DEFAULT_DECK_MODE);
    }

    private static EmperorServer maybeStartSecureServer(int wsPort) {
        SslConfig cfg = resolveSslConfig(wsPort);
        if (cfg == null) return null;
        try {
            SSLContext sslContext = buildSslContext(cfg);
            EmperorServer sslServer = new EmperorServer(cfg.port());
            sslServer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            sslServer.start();
            return sslServer;
        } catch (Exception e) {
            LOG.error("Failed to start secure WebSocket server: " + e.getMessage(), e);
            return null;
        }
    }

    private static SslConfig resolveSslConfig(int wsPort) {
        String keystorePath = firstNonBlank(
                System.getenv("WS_SSL_KEYSTORE"),
                System.getProperty("ws.ssl.keystore"),
                "config/keystore.p12"
        );
        File ksFile = new File(keystorePath);
        if (!ksFile.exists()) {
            return null;
        }
        String keystorePassword = firstNonBlank(
                System.getenv("WS_SSL_PASSWORD"),
                System.getProperty("ws.ssl.password"),
                readFileTrim("config/keystore.pass")
        );
        if (keystorePassword == null || keystorePassword.isBlank()) {
            LOG.warn("WSS not started: keystore password missing (set WS_SSL_PASSWORD or config/keystore.pass)");
            return null;
        }
        String keyPassword = firstNonBlank(
                System.getenv("WS_SSL_KEY_PASSWORD"),
                System.getProperty("ws.ssl.keyPassword"),
                keystorePassword
        );
        int sslPort = resolveInt(
                System.getenv("WS_SSL_PORT"),
                System.getProperty("ws.ssl.port"),
                wsPort + 10
        );
        if (sslPort == wsPort) {
            sslPort = wsPort + 10;
        }
        return new SslConfig(ksFile.getAbsolutePath(), keystorePassword, keyPassword, sslPort);
    }

    private static SSLContext buildSslContext(SslConfig cfg) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(cfg.keystorePath())) {
            ks.load(in, cfg.keystorePassword().toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, cfg.keyPassword().toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    private static int resolveInt(String a, String b, int fallback) {
        for (String v : new String[]{a, b}) {
            if (v != null && !v.isBlank()) {
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }

    private static String readFileTrim(String path) {
        try {
            return Files.readString(Path.of(path)).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private record SslConfig(String keystorePath, String keystorePassword, String keyPassword, int port) {}

    private static class EmperorServer extends WebSocketServer {
        private static final int MAX_CONNECTIONS_PER_IP = 3;
        // Shared across ws / wss instances so clients on both ports share rooms and we can kick duplicates
        private static final Map<WebSocket, PlayerContext> PLAYERS = new ConcurrentHashMap<>();
        private static final Map<String, WebSocket> SESSION_SOCKETS = new ConcurrentHashMap<>();
        private static final Map<String, WebSocket> ACCOUNT_SOCKETS = new ConcurrentHashMap<>();
        private static final Map<String, WebSocket> NICKNAME_SOCKETS = new ConcurrentHashMap<>();
        private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);
        private static final RoomManager ROOM_MANAGER = new RoomManager(LOG);
        private volatile Supplier<Deck.DeckConfig> deckSupplier = () -> Deck.DeckConfig.standard();
        private volatile String deckMode = DEFAULT_DECK_MODE;
        private static final Map<String, InviteRecord> INVITES = new ConcurrentHashMap<>();
        private static final Map<WebSocket, RateMeter> RATE_METERS = new ConcurrentHashMap<>();
        private static final Map<String, RateMeter> IP_RATE_METERS = new ConcurrentHashMap<>();
        private static final int RATE_LIMIT_WINDOW_MS = 1500;
        private static final int RATE_LIMIT_MAX = 6;
        private static final int IP_RATE_LIMIT_WINDOW_MS = 60_000;
        private static final int IP_RATE_LIMIT_MAX = 120;

        EmperorServer(int port) {
            super(new InetSocketAddress(port));
        }

        void applyDeckMode(String mode) {
            String normalized = mode == null ? DEFAULT_DECK_MODE : mode.trim().toLowerCase();
            Supplier<Deck.DeckConfig> supplier = switch (normalized) {
                case "random", "randomcard", "randomdeck" -> () -> Deck.DeckConfig.randomPreset();
                default -> () -> Deck.DeckConfig.standard();
            };
            this.deckSupplier = supplier;
            ROOM_MANAGER.setDeckSupplier(supplier);
            this.deckMode = normalized;
            LOG.info("Deck mode set to " + normalized);
        }

        String currentDeckMode() {
            return deckMode == null ? DEFAULT_DECK_MODE : deckMode;
        }

        @Override
        public void onStart() {
            setConnectionLostTimeout(30);
            LOG.info("WebSocket server started");
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            pruneClosedConnections();
            String id = "S" + ID_COUNTER.getAndIncrement();
            String nick = "旅人" + (1000 + ID_COUNTER.get() % 9000);
            String ip = resolveClientIp(handshake, conn.getRemoteSocketAddress());
            PlayerContext ctx = new PlayerContext(conn, id, nick, ip);
            PLAYERS.put(conn, ctx);
            SESSION_SOCKETS.put(id, conn);
            enforcePerIpLimit(ip, conn);
            dedupeSessions();

            sendJson(conn, helloPayload(ctx));
            broadcastPresence();

            LOG.info("新连接 | ip=" + ip + " | session=" + id + " | nick=" + nick);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            PlayerContext ctx = PLAYERS.get(conn);
            cleanupConnection(conn, ctx, reason == null ? "(client close)" : reason);
            broadcastPresence();
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            PlayerContext ctx = PLAYERS.get(conn);
            if (ctx == null) {
                conn.close(1008, "No session found");
                return;
            }

            if (message != null && message.length() > 4096) {
                sendError(conn, "消息过大");
                return;
            }

            ClientMessage msg;
            try {
                msg = GSON.fromJson(message, ClientMessage.class);
            } catch (JsonSyntaxException ex) {
                sendError(conn, "Invalid JSON payload");
                return;
            }
            if (msg == null || msg.type == null) {
                sendError(conn, "Message must include type");
                return;
            }

            if (shouldThrottleIp(ctx.remoteAddress, msg.type)) {
                sendError(conn, "Rate limit exceeded");
                return;
            }

            try {
                switch (msg.type) {
                    case "ping" -> sendJson(conn, Map.of("type", "pong"));
                    case "set_profile" -> handleSetProfile(ctx, msg);
                    case "change_session" -> handleChangeSession(ctx, msg);
                    case "match_online" -> handleMatchOnline(ctx);
                    case "cancel_match" -> handleCancelMatch(ctx);
                    case "play_online" -> handlePlayOnline(ctx, msg);
                    case "leave_room" -> handleLeaveRoom(ctx);
                    case "start_ai" -> handleStartAi(ctx);
                    case "play_ai" -> handlePlayAi(ctx, msg);
                    case "reset_ai" -> handleStartAi(ctx);
                    case "invite_request" -> handleInviteRequest(ctx, msg);
                    case "invite_reply" -> handleInviteReply(ctx, msg);
                    default -> sendError(conn, "Unsupported message type: " + msg.type);
                }
            } catch (Exception ex) {
                LOG.error("Server exception while handling message", ex);
                sendError(conn, "Server exception: " + ex.getClass().getSimpleName() + ": " + safeMsg(ex.getMessage()));
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            LOG.error("WebSocket server error", ex);
            if (conn != null && conn.isOpen()) {
                sendError(conn, "Server error: " + (ex.getMessage() == null ? "(no message)" : ex.getMessage()));
            }
        }

        private static String safeMsg(String s) {
            return s == null ? "(no message)" : s;
        }

        private void handleSetProfile(PlayerContext ctx, ClientMessage msg) {
            if (ctx.status == Status.IN_ROOM) {
                sendError(ctx.conn, "Cannot change profile while in a room");
                return;
            }
            if (msg.nickname != null && !msg.nickname.isBlank()) {
                String cleaned = AuthService.cleanNickname(msg.nickname, MAX_NICK_LEN);
                if (cleaned != null) ctx.nickname = cleaned;
            }
            if (msg.accountId != null && !msg.accountId.isBlank()) {
                String cleaned = cleanAccountId(msg.accountId, MAX_SESSION_LEN);
                if (cleaned == null) {
                    sendError(ctx.conn, "Invalid account id");
                    return;
                }
                boolean tokenValid = msg.accountToken != null && !msg.accountToken.isBlank() && AUTH.validateToken(cleaned, msg.accountToken);
                if (!tokenValid) {
                    LOG.warn("Account token missing/invalid for accountId=" + cleaned + ", allowing provisional bind");
                }
                if (!AUTH.accountExists(cleaned)) {
                    sendError(ctx.conn, "Account not found");
                    return;
                }
                if (ctx.accountId != null && !ctx.accountId.equals(cleaned)) {
                    sendError(ctx.conn, "Account already bound, cannot change");
                    return;
                }
                WebSocket existingAcc = ACCOUNT_SOCKETS.get(cleaned);
                if (existingAcc != null && existingAcc != ctx.conn) {
                    PlayerContext other = PLAYERS.get(existingAcc);
                    cleanupConnection(existingAcc, other, "duplicate_account");
                    tryClose(existingAcc, 1000, "Account logged in elsewhere");
                }
                ACCOUNT_SOCKETS.put(cleaned, ctx.conn);
                ctx.accountId = cleaned;
                String newSessionId = cleaned;
                WebSocket existing = SESSION_SOCKETS.get(newSessionId);
                if (existing != null && existing != ctx.conn) {
                    cleanupConnection(existing, PLAYERS.get(existing), "session_replaced");
                    tryClose(existing, 1000, "Session replaced");
                }
                SESSION_SOCKETS.remove(ctx.sessionId, ctx.conn);
                SESSION_SOCKETS.put(newSessionId, ctx.conn);
                ctx.sessionId = newSessionId;
                LOG.info("Session locked to accountId=" + ctx.sessionId + " at " + Instant.now());
            } else if (ctx.nickname != null) {
                String key = ctx.nickname.toLowerCase();
                WebSocket existingNick = NICKNAME_SOCKETS.get(key);
                if (existingNick != null && existingNick != ctx.conn) {
                    PlayerContext other = PLAYERS.get(existingNick);
                    cleanupConnection(existingNick, other, "duplicate_nickname");
                    tryClose(existingNick, 1000, "Nickname logged in elsewhere");
                }
                NICKNAME_SOCKETS.put(key, ctx.conn);
            }
            sendJson(ctx.conn, helloPayload(ctx));
            broadcastPresence();
        }

        private void handleChangeSession(PlayerContext ctx, ClientMessage msg) {
            sendError(ctx.conn, "Session ID is server-generated; client change is not supported");
        }

        private void handleMatchOnline(PlayerContext ctx) {
            if (ctx.status == Status.IN_ROOM) {
                sendError(ctx.conn, "已经在房间中");
                return;
            }
            if (shouldThrottle(ctx, "match_online")) return;

            ctx.aiGame = null;
            GameRoom room = ROOM_MANAGER.match(ctx);
            if (room == null) {
                sendJson(ctx.conn, Map.of("type", "matching", "message", "正在寻找对手..."));
                broadcastPresence();
                return;
            }

            sendJson(room.a.conn, room.matchPayloadFor(room.a));
            sendJson(room.b.conn, room.matchPayloadFor(room.b));
            LOG.info("房间 " + room.getId() + " 匹配完成");
            broadcastPresence();
        }

        private void handleCancelMatch(PlayerContext ctx) {
            ROOM_MANAGER.cancelMatch(ctx);
            sendJson(ctx.conn, Map.of("type", "match_canceled", "message", "已经取消匹配"));
            broadcastPresence();
        }

        private void handleLeaveRoom(PlayerContext ctx) {
            PlayerContext opponent = ROOM_MANAGER.leaveRoom(ctx);
            if (opponent != null && opponent.isConnected()) {
                sendJson(opponent.conn, Map.of("type", "opponent_left", "message", "对手离开房间"));
            }
            ctx.resetRoom();
        }

        private void handlePlayOnline(PlayerContext ctx, ClientMessage msg) {
            GameRoom room = ROOM_MANAGER.getRoom(msg.roomId);
            if (shouldThrottle(ctx, "play_online")) return;
            if (room == null || ctx.room == null || !Objects.equals(room.getId(), ctx.room.getId())) {
                sendError(ctx.conn, "当前不在房间或房间号不匹配");
                return;
            }

            Card card = Card.fromString(msg.card);
            if (card == null) {
                sendError(ctx.conn, "未知的牌型");
                return;
            }

            GameRoom.PlayResult result;
            try {
                result = room.play(ctx, card);
            } catch (IllegalStateException ex) {
                sendError(ctx.conn, ex.getMessage());
                return;
            }

            if (result.lockedAck != null) {
                sendJson(ctx.conn, result.lockedAck);
            }

            if (result.revealBroadcast != null) {
                sendJson(room.a.conn, result.revealBroadcast);
                sendJson(room.b.conn, result.revealBroadcast);
            }

            if (result.roundBroadcast != null) {
                sendRoomBroadcast(room, result.roundBroadcast);
                if (result.roundBroadcast.isGameOver()) {
                    ROOM_MANAGER.retireRoom(room);
                    broadcastPresence();
                }
            }
        }

        private void handleStartAi(PlayerContext ctx) {
            ctx.status = Status.IDLE;
            ctx.room = null;
            Deck.DeckConfig cfg = deckSupplier.get();
            ctx.aiGame = new AiGame(cfg);
            sendJson(ctx.conn, ctx.aiGame.statePayload("ai_ready"));
        }

        private void handlePlayAi(PlayerContext ctx, ClientMessage msg) {
            if (ctx.aiGame == null) {
                handleStartAi(ctx);
            }
            Card card = Card.fromString(msg.card);
            if (card == null) {
                sendError(ctx.conn, "未知的牌型");
                return;
            }
            try {
                RoundResult result = ctx.aiGame.play(card);
                Map<String, Object> payload = result.toPayload();
                payload.put("type", "ai_round");
                sendJson(ctx.conn, payload);
            } catch (IllegalStateException ex) {
                sendError(ctx.conn, ex.getMessage());
            }
        }

        private void handleInviteRequest(PlayerContext ctx, ClientMessage msg) {
            if (ctx.status == Status.IN_ROOM || ctx.status == Status.MATCHING) {
                sendError(ctx.conn, "你正在对局/匹配中，无法发起邀请");
                return;
            }
            if (msg.targetSessionId == null || msg.targetSessionId.isBlank()) {
                sendError(ctx.conn, "目标会话不能为空");
                return;
            }
            PlayerContext target = PLAYERS.values().stream()
                    .filter(p -> msg.targetSessionId.equals(p.sessionId))
                    .findFirst().orElse(null);
            if (target == null || !target.isConnected()) {
                sendError(ctx.conn, "对方不在线");
                return;
            }
            if (ctx.accountId != null && target.accountId != null && ctx.accountId.equals(target.accountId)) {
                sendError(ctx.conn, "Cannot invite yourself");
                return;
            }
            if (target.status == Status.IN_ROOM || target.status == Status.MATCHING) {
                sendJson(ctx.conn, Map.of("type", "invite_busy", "message", "对方正在对局中"));
                return;
            }
            String inviteId = UUID.randomUUID().toString();
            INVITES.put(inviteId, new InviteRecord(ctx.sessionId, target.sessionId, System.currentTimeMillis()));
            sendJson(target.conn, Map.of(
                    "type", "invite_offer",
                    "fromSessionId", ctx.sessionId,
                    "fromNickname", ctx.nickname,
                    "inviteId", inviteId
            ));
        }

        private void handleInviteReply(PlayerContext ctx, ClientMessage msg) {
            if (msg.inviteId == null || msg.inviteId.isBlank()) {
                sendError(ctx.conn, "邀请ID缺失");
                return;
            }
            InviteRecord rec = INVITES.remove(msg.inviteId);
            if (rec == null) {
                sendError(ctx.conn, "邀请不存在或已过期");
                return;
            }
            if (!rec.targetSession.equals(ctx.sessionId)) {
                sendError(ctx.conn, "该邀请不属于当前会话");
                return;
            }
            PlayerContext inviter = PLAYERS.values().stream()
                    .filter(p -> rec.inviterSession.equals(p.sessionId))
                    .findFirst().orElse(null);
            if (inviter != null && inviter.isConnected()) {
                if (Boolean.TRUE.equals(msg.accept)) {
                    // 双方都需空闲
                    if (inviter.status == Status.IN_ROOM || inviter.status == Status.MATCHING || ctx.status == Status.IN_ROOM || ctx.status == Status.MATCHING) {
                        sendJson(inviter.conn, Map.of(
                                "type", "invite_result",
                                "inviteId", msg.inviteId,
                                "accept", false,
                                "reason", "一方正在对局/匹配"
                        ));
                        return;
                    }
                    GameRoom room = ROOM_MANAGER.createRoom(inviter, ctx);
                    sendJson(inviter.conn, room.matchPayloadFor(inviter));
                    sendJson(ctx.conn, room.matchPayloadFor(ctx));
                    broadcastPresence();
                    return;
                }
                sendJson(inviter.conn, Map.of(
                        "type", "invite_result",
                        "inviteId", msg.inviteId,
                        "accept", Boolean.TRUE.equals(msg.accept),
                        "reason", Boolean.TRUE.equals(msg.accept) ? "" : "对方拒绝了邀请"
                ));
            }
        }

        private void broadcastPresence() {
            pruneClosedConnections();
            dedupeSessions();
            Map<String, Object> payload = Map.of(
                    "type", "presence",
                    "online", PLAYERS.size(),
                    "players", PLAYERS.values().stream()
                            .filter(PlayerContext::isConnected)
                            .map(p -> Map.of(
                                    "sessionId", p.sessionId,
                                    "nickname", p.nickname,
                                    "ip", p.remoteAddress == null ? "" : p.remoteAddress,
                                    "status", p.status == null ? Status.IDLE.name() : p.status.name()
                            ))
                            .toList()
            );
            broadcast(GSON.toJson(payload));
        }

        public void broadcastNotice(String title, String message, String tone) {
            Map<String, Object> payload = Map.of(
                    "type", "notice",
                    "title", (title == null || title.isBlank()) ? "Notice" : title,
                    "message", message == null ? "" : message,
                    "tone", (tone == null || tone.isBlank()) ? "info" : tone
            );
            broadcast(GSON.toJson(payload));
        }

        private void sendJson(WebSocket conn, Map<String, ?> payload) {
            if (conn != null && conn.isOpen()) {
                conn.send(GSON.toJson(payload));
            }
        }

        private void sendRoomBroadcast(GameRoom room, RoundBroadcast broadcast) {
            String json = GSON.toJson(broadcast.toPayload());
            room.a.conn.send(json);
            room.b.conn.send(json);
        }

        private Map<String, Object> helloPayload(PlayerContext ctx) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "hello");
            payload.put("sessionId", ctx.sessionId);
            payload.put("nickname", ctx.nickname);
            payload.put("online", PLAYERS.size());
            payload.put("message", "connected");
            return payload;
        }

        private void sendError(WebSocket conn, String message) {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "error");
            error.put("message", message);
            sendJson(conn, error);
            LOG.warn("error response: " + message);
        }

        private record ClientMessage(String type,
                                     String card,
                                     String nickname,
                                     String accountId,
                                     String accountToken,
                                     String sessionId,
                                     String roomId,
                                     String targetSessionId,
                                     String inviteId,
                                     Boolean accept) {
        }

        private String resolveClientIp(ClientHandshake handshake, InetSocketAddress remote) {
            String fwd = handshake.getFieldValue("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) {
                String first = fwd.split(",")[0].trim();
                if (!first.isBlank()) return first;
            }
            String real = handshake.getFieldValue("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
            if (remote != null && remote.getAddress() != null) {
                return remote.getAddress().getHostAddress();
            }
            return "(unknown)";
        }

        private void pruneClosedConnections() {
            for (Map.Entry<WebSocket, PlayerContext> entry : PLAYERS.entrySet()) {
                WebSocket socket = entry.getKey();
                PlayerContext ctx = entry.getValue();
                if (socket == null || !socket.isOpen()) {
                    cleanupConnection(socket, ctx, "stale_socket");
                }
            }
        }

        private void enforcePerIpLimit(String ip, WebSocket preferred) {
            if (ip == null || ip.isBlank()) return;
            var sameIp = PLAYERS.entrySet().stream()
                    .filter(e -> ip.equals(e.getValue().remoteAddress))
                    .sorted((a, b) -> {
                        PlayerContext pa = a.getValue();
                        PlayerContext pb = b.getValue();
                        boolean aa = pa != null && pa.nickname != null && pa.nickname.startsWith("旅人");
                        boolean bb = pb != null && pb.nickname != null && pb.nickname.startsWith("旅人");
                        if (aa != bb) return aa ? -1 : 1; // 旅人优先被踢
                        long ta = pa == null ? 0 : pa.connectedAt;
                        long tb = pb == null ? 0 : pb.connectedAt;
                        return Long.compare(ta, tb); // 旧的先踢
                    })
                    .toList();
            int over = sameIp.size() - MAX_CONNECTIONS_PER_IP;
            if (over <= 0) return;

            LOG.warn("Too many connections from ip=" + ip + " (" + sameIp.size() + "), trimming to " + MAX_CONNECTIONS_PER_IP);
            for (Map.Entry<WebSocket, PlayerContext> entry : sameIp) {
                if (over <= 0) break;
                if (preferred != null && entry.getKey() == preferred) continue;
                cleanupConnection(entry.getKey(), entry.getValue(), "ip_limit");
                tryClose(entry.getKey(), 1013, "Too many connections from same IP");
                over--;
            }
        }

        private void cleanupConnection(WebSocket conn, PlayerContext ctx, String reason) {
            if (ctx == null) {
                ctx = PLAYERS.remove(conn);
            } else {
                PLAYERS.remove(conn, ctx);
            }
            RATE_METERS.remove(conn);
            if (ctx == null) return;

            SESSION_SOCKETS.remove(ctx.sessionId);
            if (ctx.accountId != null) {
                ACCOUNT_SOCKETS.remove(ctx.accountId, conn);
            }
            if (ctx.nickname != null) {
                NICKNAME_SOCKETS.remove(ctx.nickname.toLowerCase(), conn);
            }
            PlayerContext finalCtx = ctx;
            INVITES.entrySet().removeIf(e -> e.getValue().inviterSession().equals(finalCtx.sessionId)
                    || e.getValue().targetSession().equals(finalCtx.sessionId));

            ROOM_MANAGER.abortMatch(ctx);
            PlayerContext opponent = ROOM_MANAGER.leaveRoom(ctx);
            if (opponent != null && opponent.isConnected()) {
                sendJson(opponent.conn, Map.of("type", "opponent_left", "message", "对手离线，房间已关闭"));
            }
            if (ctx.accountId != null) {
                AUTH.clearActive(ctx.accountId);
            }

            LOG.info("清理连接 | ip=" + ctx.remoteAddress + " | session=" + ctx.sessionId + " | nick=" + ctx.nickname + " | reason=" + reason);
        }

        void forceLogout(String accountId, WebSocket exclude) {
            if (accountId == null) return;
            String target = accountId;
            for (Map.Entry<WebSocket, PlayerContext> entry : PLAYERS.entrySet()) {
                if (exclude != null && entry.getKey() == exclude) continue;
                PlayerContext ctx = entry.getValue();
                if (ctx != null && ctx.accountId != null && target.equals(ctx.accountId)) {
                    cleanupConnection(entry.getKey(), ctx, "duplicate_login");
                    tryClose(entry.getKey(), 1000, "Session replaced by login");
                    LOG.info("强制登出 | accountId=" + ctx.accountId + " | session=" + ctx.sessionId);
                }
            }
        }

        private boolean shouldThrottle(PlayerContext ctx, String label) {
            if (ctx == null || ctx.conn == null) return false;
            RateMeter meter = RATE_METERS.computeIfAbsent(ctx.conn, k -> new RateMeter());
            long now = System.currentTimeMillis();
            synchronized (meter) {
                if (now - meter.windowStart > RATE_LIMIT_WINDOW_MS) {
                    meter.windowStart = now;
                    meter.count = 0;
                }
                meter.count++;
                if (meter.count > RATE_LIMIT_MAX) {
                    LOG.warn("Rate limit [" + label + "] triggered for session " + ctx.sessionId);
                    sendError(ctx.conn, "操作过于频繁，请稍候再试");
                    return true;
                }
            }
            return false;
        }

        private boolean shouldThrottleIp(String ip, String label) {
            if (ip == null || ip.isBlank()) ip = "(unknown)";
            RateMeter meter = IP_RATE_METERS.computeIfAbsent(ip, k -> new RateMeter());
            long now = System.currentTimeMillis();
            synchronized (meter) {
                if (now - meter.windowStart > IP_RATE_LIMIT_WINDOW_MS) {
                    meter.windowStart = now;
                    meter.count = 0;
                }
                meter.count++;
                if (meter.count > IP_RATE_LIMIT_MAX) {
                    LOG.warn("IP rate limit [" + label + "] triggered for ip " + ip);
                    return true;
                }
            }
            return false;
        }

        private void tryClose(WebSocket conn, int code, String reason) {
            if (conn != null && conn.isOpen()) {
                try {
                    conn.close(code, reason);
                } catch (Exception ignored) {
                }
            }
        }

        private void dedupeSessions() {
            Map<String, PlayerContext> latest = new ConcurrentHashMap<>();
            PLAYERS.forEach((socket, ctx) -> {
                if (ctx == null) return;
                PlayerContext prev = latest.get(ctx.sessionId);
                if (prev == null || ctx.connectedAt > prev.connectedAt) {
                    latest.put(ctx.sessionId, ctx);
                }
            });
            PLAYERS.forEach((socket, ctx) -> {
                if (ctx == null) return;
                PlayerContext keep = latest.get(ctx.sessionId);
                if (keep != ctx) {
                    cleanupConnection(socket, ctx, "duplicate_session");
                    tryClose(socket, 1000, "Session taken over");
                }
            });
        }

        boolean hasAccountOnline(String accountId) {
            if (accountId == null) return false;
            WebSocket ws = ACCOUNT_SOCKETS.get(accountId);
            if (ws == null) return false;
            return ws.isOpen();
        }

        boolean hasNicknameOnline(String nickname) {
            if (nickname == null) return false;
            WebSocket ws = NICKNAME_SOCKETS.get(nickname.toLowerCase());
            if (ws == null) return false;
            return ws.isOpen();
        }

        private static class RateMeter {
            long windowStart;
            int count;
        }
    }

    private static String cleanAccountId(String raw, int maxLen) {
        if (raw == null) return null;
        String id = raw.trim();
        if (id.isEmpty() || id.length() > maxLen) return null;
        if (!id.matches("[A-Za-z0-9_-]+")) return null;
        return id;
    }

    private static class StaticHttp {
        private static final ServerLogger HTTP_LOG = ServerLogger.get(StaticHttp.class);
        private static final int HTTP_RATE_LIMIT_WINDOW_MS = 60_000;
        private static final int HTTP_RATE_LIMIT_MAX = 60;
        private static final int MAX_API_BODY_BYTES = 16_384;
        private static final Map<String, HttpRateLimiter> HTTP_RATE_LIMITS = new ConcurrentHashMap<>();
        private static final String DEFAULT_ALLOWED_ORIGINS = "http://localhost,http://127.0.0.1";
        static HttpServer start(int port) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                server.createContext("/", StaticHttp::handle);
                server.setExecutor(Executors.newFixedThreadPool(4));
                server.start();
                return server;
            } catch (IOException e) {
                LOG.warn("Failed to start HTTP server: " + e.getMessage());
                return null;
            }
        }

        private static void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (Objects.equals(path, "/")) path = "/index.html";
            String resourcePath = path.startsWith("/") ? path.substring(1) : path;

            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (resourcePath.startsWith("api/")) {
                handleApi(exchange, resourcePath);
                return;
            }

            byte[] body = readResource(resourcePath);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", contentType(resourcePath));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private static byte[] readResource(String path) throws IOException {
            InputStream in = Main.class.getClassLoader().getResourceAsStream(path);
            if (in == null) return null;
            try (in) {
                return in.readAllBytes();
            }
        }

        private static void handleApi(HttpExchange exchange, String resourcePath) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String ip = clientIp(exchange);
            if (shouldThrottleHttp(ip)) {
                exchange.sendResponseHeaders(429, -1);
                return;
            }
            byte[] raw;
            try {
                raw = readRequestBodyLimited(exchange, MAX_API_BODY_BYTES);
            } catch (PayloadTooLargeException ex) {
                // 拒绝超大包体，避免 readAllBytes 带来的内存放大风险。
                exchange.sendResponseHeaders(413, -1);
                return;
            }
            Map<String, Object> req = Map.of();
            try {
                Map<String, Object> parsed = GSON.fromJson(new String(raw, StandardCharsets.UTF_8), Map.class);
                if (parsed != null) {
                    req = parsed;
                }
            } catch (Exception ignored) {}
            String nickname = safeStr(req.get("nickname"));
            String password = safeStr(req.get("password"));
            if (nickname.length() > MAX_NICK_LEN || password.length() > MAX_PASS_LEN) {
                writeJson(exchange, ApiResult.fail("Payload field too long"));
                return;
            }
            if (resourcePath.equals("api/register")) {
                ApiResult res = AUTH.register(ip, nickname, password);
                writeJson(exchange, res);
                return;
            }
            if (resourcePath.equals("api/login")) {
                ApiResult res = AUTH.login(ip, nickname, password);
                writeJson(exchange, res);
                return;
            }
            if (resourcePath.equals("api/validate")) {
                ApiResult res = AUTH.validate(ip, nickname, password);
                writeJson(exchange, res);
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        }

        private static void writeJson(HttpExchange ex, ApiResult res) throws IOException {
            byte[] out = GSON.toJson(res).getBytes(StandardCharsets.UTF_8);
            Headers headers = ex.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");
            // 为避免前端跨域时 fetch 直接抛 400，统一用 200 返回业务状态
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        }

        private static byte[] readRequestBodyLimited(HttpExchange exchange, int maxBytes) throws IOException {
            try (InputStream in = exchange.getRequestBody();
                 ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 4096))) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > maxBytes) {
                        throw new PayloadTooLargeException();
                    }
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        }

        private static final class PayloadTooLargeException extends IOException {
        }

        private static void addCorsHeaders(HttpExchange exchange) {
            Headers headers = exchange.getResponseHeaders();
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            String allowOrigin = (origin == null || origin.isBlank()) ? "*" : origin;
            headers.set("Access-Control-Allow-Origin", allowOrigin);
            headers.set("Vary", "Origin");
            // 仅在非通配时允许携带凭证，避免浏览器拒绝
            if (!"*".equals(allowOrigin)) {
                headers.set("Access-Control-Allow-Credentials", "true");
            }
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
        }

        private static boolean isOriginAllowed(HttpExchange exchange) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin)) {
                return true;
            }
            String allowed = System.getenv("ALLOWED_API_ORIGINS");
            if (allowed == null || allowed.isBlank()) {
                allowed = DEFAULT_ALLOWED_ORIGINS;
            }
            for (String candidate : allowed.split(",")) {
                if (origin.equalsIgnoreCase(candidate.trim())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean shouldThrottleHttp(String ip) {
            if (ip == null || ip.isBlank()) ip = "(unknown)";
            HttpRateLimiter limiter = HTTP_RATE_LIMITS.computeIfAbsent(ip, ignored -> new HttpRateLimiter());
            if (!limiter.allow()) {
                HTTP_LOG.warn("HTTP rate limit exceeded for ip " + ip);
                return true;
            }
            return false;
        }

        private static class HttpRateLimiter {
            long windowStart;
            int count;

            synchronized boolean allow() {
                long now = System.currentTimeMillis();
                if (now - windowStart > HTTP_RATE_LIMIT_WINDOW_MS) {
                    windowStart = now;
                    count = 0;
                }
                count++;
                return count <= HTTP_RATE_LIMIT_MAX;
            }
        }

        private static String safeStr(Object o) {
            return o == null ? "" : String.valueOf(o);
        }

        private static String contentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }

        private static String clientIp(HttpExchange exchange) {
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isBlank()) return first;
            }
            String real = exchange.getRequestHeaders().getFirst("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
            if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
                return exchange.getRemoteAddress().getAddress().getHostAddress();
            }
            return "(unknown)";
        }
    }

    /* ========================= Simple Auth Service (in-memory) ========================= */
    private static void requestSessionKick(String accountId) {
        if (accountId == null) return;
        EmperorServer server = ACTIVE_SERVER;
        if (server != null) {
            server.forceLogout(accountId, null);
        }
    }



private static class AuthService {
        private final Map<String, Long> activeSessions = new ConcurrentHashMap<>();
        private static final long LOGIN_HOLD_MS = 0; // no hold; allow immediate re-login
    private static final long TOKEN_TTL_MS = 24 * 60 * 60 * 1000; // 24h token
    private final byte[] tokenSecret = initSecret();

    AuthService() {
        ensureTables();
    }

        ApiResult register(String ip, String nickname, String password) {
            String nick = cleanNickname(nickname, MAX_NICK_LEN);
            if (nick == null) return ApiResult.fail("Invalid nickname");
            String pass = cleanPassword(password, MAX_PASS_LEN);
            if (pass == null) return ApiResult.fail("Invalid password");
            String passHash = pass; // stored as plain per current requirement
            try (Connection conn = work.emperor.util.Database.open()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM registration_log WHERE ip=? AND DATE(created_at)=CURDATE()")) {
                    ps.setString(1, ip);
                    try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return ApiResult.fail("This IP has already registered today");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE nickname=? LIMIT 1")) {
                ps.setString(1, nick);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return ApiResult.fail("Nickname already exists");
                }
            }

            long userId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users(nickname,password_hash,created_at,last_login_ip,last_login_at) VALUES(?, ?, NOW(), ?, NULL)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nick);
                ps.setString(2, passHash);
                ps.setString(3, ip);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    userId = rs.next() ? rs.getLong(1) : -1;
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO registration_log(ip) VALUES(?)")) {
                ps.setString(1, ip);
                ps.executeUpdate();
            }

            String accountId = toAccountId(userId);
            LOG.info("Register success | ip=" + ip + " | nick=" + nick + " | userId=" + userId);
            return ApiResult.ok("Register success", accountId, issueToken(accountId));
        } catch (SQLException e) {
            LOG.error("Register failed: " + e.getMessage(), e);
            return ApiResult.fail("Database error: " + e.getMessage());
        }
    }

        ApiResult login(String ip, String nickname, String password) {
            String nick = cleanNickname(nickname, MAX_NICK_LEN);
            if (nick == null) return ApiResult.fail("Invalid nickname");
            String pass = cleanPassword(password, MAX_PASS_LEN);
            if (pass == null) return ApiResult.fail("Invalid password");
            purgeExpiredSessions();
            try (Connection conn = work.emperor.util.Database.open()) {
                long userId = -1;
                String dbHash = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id, password_hash FROM users WHERE nickname=? LIMIT 1")) {
                ps.setString(1, nick);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong("id");
                        dbHash = rs.getString("password_hash");
                    }
                }
            }
            if (dbHash == null) return ApiResult.fail("User not found");
            if (!Objects.equals(dbHash, pass)) return ApiResult.fail("Invalid password");

                String accountId = toAccountId(userId);
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET last_login_at=NOW(), last_login_ip=? WHERE id=?")) {
                    ps.setString(1, ip);
                    ps.setLong(2, userId);
                    ps.executeUpdate();
                }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO login_log(user_id, nickname, ip) VALUES(?, ?, ?)")) {
                ps.setLong(1, userId);
                ps.setString(2, nick);
                ps.setString(3, ip);
                ps.executeUpdate();
            }

            markActive(accountId);
            LOG.info("Login success | ip=" + ip + " | nick=" + nick + " | userId=" + userId);
            return ApiResult.ok("Login success", accountId, issueToken(accountId));
        } catch (SQLException e) {
            LOG.error("Login failed: " + e.getMessage(), e);
            return ApiResult.fail("Database error: " + e.getMessage());
        }
    }

        ApiResult validate(String ip, String nickname, String password) {
            String nick = cleanNickname(nickname, MAX_NICK_LEN);
            if (nick == null) return ApiResult.fail("Invalid nickname");
            String pass = cleanPassword(password, MAX_PASS_LEN);
            if (pass == null) return ApiResult.fail("Invalid password");
            purgeExpiredSessions();
            try (Connection conn = work.emperor.util.Database.open()) {
                long userId = -1;
                String dbHash = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id, password_hash FROM users WHERE nickname=? LIMIT 1")) {
                ps.setString(1, nick);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong("id");
                        dbHash = rs.getString("password_hash");
                    }
                }
            }
            if (dbHash == null) {
                if (userId > 0) activeSessions.remove(toAccountId(userId));
                return ApiResult.fail("User not found");
            }
            if (!Objects.equals(dbHash, pass)) {
                if (userId > 0) activeSessions.remove(toAccountId(userId));
                return ApiResult.fail("Invalid password");
            }
                String accountId = toAccountId(userId);
                return ApiResult.ok("OK", accountId, issueToken(accountId));
            } catch (SQLException e) {
                LOG.error("Validate failed: " + e.getMessage(), e);
                return ApiResult.fail("Database error: " + e.getMessage());
            }
    }

    private void ensureTables() {
        try (Connection conn = work.emperor.util.Database.open()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                        nickname VARCHAR(64) NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_login_at TIMESTAMP NULL DEFAULT NULL,
                        last_login_ip VARCHAR(64) DEFAULT NULL,
                        PRIMARY KEY (id),
                        KEY idx_nickname (nickname)
                    )
                    """)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS registration_log (
                        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                        ip VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        register_date DATE AS (DATE(created_at)) STORED,
                        PRIMARY KEY (id),
                        KEY idx_ip_date (ip, register_date)
                    )
                    """)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS login_log (
                        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                        user_id BIGINT UNSIGNED DEFAULT NULL,
                        nickname VARCHAR(64) NOT NULL,
                        ip VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_login_ip_date (ip, created_at)
                    )
                    """)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS session_tokens (
                        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                        account_id VARCHAR(64) NOT NULL,
                        token VARCHAR(512) NOT NULL,
                        issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        expires_at DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_token (token),
                        KEY idx_account (account_id)
                    )
                    """)) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.error("Init auth tables failed: " + e.getMessage(), e);
        }
    }

    private byte[] initSecret() {
        String fromEnv = System.getenv("AUTH_SECRET");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.getBytes(StandardCharsets.UTF_8);
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    String issueToken(String accountId) {
        if (accountId == null || accountId.isBlank()) return null;
        long ts = System.currentTimeMillis();
        long expires = ts + TOKEN_TTL_MS;
        String payload = accountId + ':' + ts;
        String sig = hmac(payload);
        if (sig == null) return null;
        String token = payload + ':' + sig;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        persistToken(accountId, encoded, expires);
        return encoded;
    }

    boolean validateToken(String accountId, String token) {
        if (accountId == null || token == null || token.isBlank()) return false;
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return false;
        }
        String[] parts = decoded.split(":", 3);
        if (parts.length != 3) return false;
        if (!accountId.equals(parts[0])) return false;
        long ts;
        try {
            ts = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - ts > TOKEN_TTL_MS || ts > now + 60_000) {
            return false;
        }
        String payload = parts[0] + ":" + parts[1];
        String sig = hmac(payload);
        boolean sigOk = sig != null && sig.equals(parts[2]);
        return sigOk && tokenExists(accountId, token);
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret, "HmacSHA256"));
            byte[] out = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            LOG.warn("token signing failed: " + e.getMessage());
            return null;
        }
    }

    private String hash(String raw) {
        return raw == null ? "" : raw; // still plain text (legacy requirement)
    }

    private void persistToken(String accountId, String token, long expiresAtMs) {
        try (Connection conn = work.emperor.util.Database.open();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO session_tokens(account_id, token, issued_at, expires_at) VALUES(?, ?, ?, ?)")) {
            ps.setString(1, accountId);
            ps.setString(2, token);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(4, new Timestamp(expiresAtMs));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("Failed to persist token: " + e.getMessage());
        }
    }

    private boolean tokenExists(String accountId, String token) {
        purgeExpiredTokens();
        try (Connection conn = work.emperor.util.Database.open();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM session_tokens WHERE account_id=? AND token=? AND expires_at>=? LIMIT 1")) {
            ps.setString(1, accountId);
            ps.setString(2, token);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.warn("Token lookup failed: " + e.getMessage());
            return false;
        }
    }

    private void purgeExpiredTokens() {
        try (Connection conn = work.emperor.util.Database.open();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM session_tokens WHERE expires_at < ?")) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("Token purge failed: " + e.getMessage());
        }
    }

        static String cleanNickname(String raw, int maxLen) {
            if (raw == null) return null;
            String nick = raw.trim();
            if (nick.isEmpty() || nick.length() > maxLen) return null;
            nick = nick.replaceAll("\\p{Cntrl}", "");
            return nick.isEmpty() ? null : nick;
        }

        static String cleanPassword(String raw, int maxLen) {
            if (raw == null) return null;
            String pass = raw.trim();
            if (pass.isEmpty() || pass.length() > maxLen) return null;
            if (pass.chars().anyMatch(Character::isISOControl)) return null;
            // 不允许包含空格，避免误填和弱口令
            if (pass.contains(" ") || pass.contains("\t")) return null;
            return pass;
        }

    private void purgeExpiredSessions() {
        long now = System.currentTimeMillis();
        activeSessions.entrySet().removeIf(e -> now - e.getValue() > LOGIN_HOLD_MS);
    }

    private boolean isActive(String key) {
        Long ts = activeSessions.get(key);
        if (ts == null) return false;
        return (System.currentTimeMillis() - ts) <= LOGIN_HOLD_MS;
    }

    private void markActive(String accountId) {
        if (accountId == null) return;
        activeSessions.put(accountId, System.currentTimeMillis());
    }

    void clearActive(String accountId) {
        if (accountId == null) return;
        activeSessions.remove(accountId);
    }

    boolean accountExists(String accountId) {
        long userId = parseAccountId(accountId);
        if (userId <= 0) return false;
        try (Connection conn = work.emperor.util.Database.open();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE id=? LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.warn("Account check failed: " + e.getMessage());
            return false;
        }
    }

    private static String toAccountId(long userId) {
        return "U" + userId;
    }

    private static long parseAccountId(String accountId) {
        if (accountId == null || accountId.length() < 2 || !accountId.startsWith("U")) return -1;
        try {
            return Long.parseLong(accountId.substring(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}

private record ApiResult(boolean ok, String message, String accountId, String token) {
        static ApiResult ok(String msg, String accountId, String token) { return new ApiResult(true, msg, accountId, token); }
        static ApiResult ok(String msg, String accountId) { return new ApiResult(true, msg, accountId, null); }
        static ApiResult ok(String msg) { return new ApiResult(true, msg, null, null); }
        static ApiResult fail(String msg) { return new ApiResult(false, msg, null, null); }
    }

    private record InviteRecord(String inviterSession, String targetSession, long createdAtMs) {}
}




