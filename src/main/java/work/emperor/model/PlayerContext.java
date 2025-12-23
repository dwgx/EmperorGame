package work.emperor.model;

import org.java_websocket.WebSocket;
import work.emperor.manager.AiGame;
import work.emperor.manager.GameRoom;

public class PlayerContext {
    public final WebSocket conn;
    public String sessionId;
    public String nickname;
    public String accountId;
    public final String remoteAddress;
    public final long connectedAt;
    public Status status = Status.IDLE;
    public GameRoom room;
    public AiGame aiGame;

    public PlayerContext(WebSocket conn, String sessionId, String nickname) {
        this(conn, sessionId, nickname, null);
    }

    public PlayerContext(WebSocket conn, String sessionId, String nickname, String remoteAddress) {
        this.conn = conn;
        this.sessionId = sessionId;
        this.nickname = nickname;
        this.remoteAddress = remoteAddress;
        this.connectedAt = System.currentTimeMillis();
    }

    public boolean isConnected() {
        return conn != null && conn.isOpen();
    }

    public void resetRoom() {
        room = null;
        status = Status.IDLE;
    }
}
