package work.emperor.manager;

import java.util.Map;

public class RoundResult {
    private final int round;
    private final Card playerCard;
    private final Card opponentCard;
    private final Outcome outcome;
    private final int playerScore;
    private final int opponentScore;
    private final Map<String, Integer> playerRemaining;
    private final Map<String, Integer> opponentRemaining;
    private final boolean gameOver;
    private final String finalResult;

    public RoundResult(int round, Card playerCard, Card opponentCard, Outcome outcome,
                       int playerScore, int opponentScore,
                       Map<String, Integer> playerRemaining, Map<String, Integer> opponentRemaining,
                       boolean gameOver, String finalResult) {
        this.round = round;
        this.playerCard = playerCard;
        this.opponentCard = opponentCard;
        this.outcome = outcome;
        this.playerScore = playerScore;
        this.opponentScore = opponentScore;
        this.playerRemaining = playerRemaining;
        this.opponentRemaining = opponentRemaining;
        this.gameOver = gameOver;
        this.finalResult = finalResult;
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("round", round);
        payload.put("playerCard", playerCard.name());
        payload.put("opponentCard", opponentCard.name());
        payload.put("outcome", outcome.name());
        payload.put("playerScore", playerScore);
        payload.put("opponentScore", opponentScore);
        payload.put("playerRemaining", playerRemaining);
        payload.put("opponentRemaining", opponentRemaining);
        payload.put("gameOver", gameOver);
        payload.put("finalResult", finalResult);
        return payload;
    }
}
