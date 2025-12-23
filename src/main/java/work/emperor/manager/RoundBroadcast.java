package work.emperor.manager;

import java.util.HashMap;
import java.util.Map;

public class RoundBroadcast {
    private final String roomId;
    private final int round;
    private final boolean gameOver;
    private final Map<String, String> outcome;
    private final String finalResult;
    private final Map<String, String> cards;
    private final boolean nextRoundPublic;
    private String madmanEffect;

    private Map<String, Integer> scores = Map.of();
    private Map<String, Map<String, Integer>> remaining = Map.of();

    public RoundBroadcast(String roomId, int round, boolean gameOver,
                          Map<String, String> outcome, String finalResult,
                          Map<String, String> cards, boolean nextRoundPublic) {
        this.roomId = roomId;
        this.round = round;
        this.gameOver = gameOver;
        this.outcome = outcome;
        this.finalResult = finalResult;
        this.cards = cards;
        this.nextRoundPublic = nextRoundPublic;
    }

    public void setScores(Map<String, Integer> scores) {
        this.scores = scores != null ? new HashMap<>(scores) : Map.of();
    }

    public void setRemaining(Map<String, Map<String, Integer>> remaining) {
        this.remaining = remaining != null ? new HashMap<>(remaining) : Map.of();
    }

    public void setMadmanEffect(String madmanEffect) {
        this.madmanEffect = madmanEffect;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "online_round");
        payload.put("roomId", roomId);
        payload.put("round", round);
        payload.put("gameOver", gameOver);
        payload.put("outcome", outcome);
        payload.put("finalResult", finalResult);
        payload.put("cards", cards);
        payload.put("nextRoundPublic", nextRoundPublic);
        payload.put("scores", scores);
        payload.put("remaining", remaining);
        if (madmanEffect != null) {
            payload.put("madmanEffect", madmanEffect);
        }
        return payload;
    }
}
