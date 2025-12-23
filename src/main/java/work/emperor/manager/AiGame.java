package work.emperor.manager;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class AiGame {
    private Deck playerDeck;
    private Deck aiDeck;
    private int round = 1;
    private int playerScore = 0;
    private int aiScore = 0;
    private String lastMadmanEffect = null;

    public AiGame() {
        this(Deck.DeckConfig.standard());
    }

    public AiGame(Deck.DeckConfig config) {
        this.playerDeck = new Deck(config);
        this.aiDeck = new Deck(config);
    }

    public synchronized RoundResult play(Card playerCard) {
        if (playerDeck.remaining() == 0 || aiDeck.remaining() == 0) throw new IllegalStateException("本局已经结束，请重开");
        if (!playerDeck.hasCard(playerCard)) throw new IllegalStateException("这张牌已经用完");

        playerDeck.use(playerCard);
        Card aiCard = aiDeck.drawRandom();

        boolean madmanPresent = (playerCard == Card.MADMAN) || (aiCard == Card.MADMAN);
        boolean redoRound = false;
        lastMadmanEffect = null;
        if (madmanPresent) {
            int roll = ThreadLocalRandom.current().nextInt(3);
            switch (roll) {
                case 0 -> {
                    Card takeP = playerDeck.drawRandomRemaining();
                    Card takeAi = aiDeck.drawRandomRemaining();
                    if (takeP != null && takeAi != null) {
                        playerDeck.putBack(takeAi);
                        aiDeck.putBack(takeP);
                    }
                    lastMadmanEffect = "swap_random";
                }
                case 1 -> {
                    playerDeck.drawRandomRemaining();
                    aiDeck.drawRandomRemaining();
                    lastMadmanEffect = "discard_random";
                }
                default -> {
                    if (playerCard != Card.MADMAN) playerDeck.putBack(playerCard);
                    if (aiCard != Card.MADMAN) aiDeck.putBack(aiCard);
                    redoRound = true;
                    lastMadmanEffect = "redo_round";
                }
            }
        }

        Outcome outcome = redoRound ? Outcome.DRAW : Outcome.versus(playerCard, aiCard);
        String forced = resolveForcedWinner(playerCard, aiCard, outcome, madmanPresent);
        if (!redoRound && forced == null) {
            switch (outcome) {
                case WIN -> playerScore++;
                case LOSE -> aiScore++;
                case DRAW -> {}
            }
        }

        boolean gameOver = (forced != null) || (!redoRound && playerDeck.remaining() == 0) || (!redoRound && aiDeck.remaining() == 0);
        String finalResult = null;
        if (forced != null) {
            finalResult = forced;
            if (forced.equals("PLAYER")) playerScore = Math.max(playerScore, aiScore + 1);
            else aiScore = Math.max(aiScore, playerScore + 1);
        } else if (gameOver) {
            if (playerScore == aiScore) finalResult = "DRAW";
            else finalResult = playerScore > aiScore ? "PLAYER" : "AI";
        }

        RoundResult result = new RoundResult(
                round, playerCard, aiCard, outcome,
                playerScore, aiScore,
                playerDeck.snapshot(), aiDeck.snapshot(),
                gameOver, finalResult
        );
        if (!redoRound) {
            round++;
        }
        return result;
    }

    private String resolveForcedWinner(Card playerCard, Card aiCard, Outcome outcome, boolean madmanPresent) {
        if (madmanPresent) return null;
        if (playerCard == Card.TRAITOR && aiCard == Card.EMPEROR) return "PLAYER";
        if (aiCard == Card.TRAITOR && playerCard == Card.EMPEROR) return "AI";
        if (playerCard == Card.SLAVE && aiCard == Card.EMPEROR) return "PLAYER";
        if (aiCard == Card.SLAVE && playerCard == Card.EMPEROR) return "AI";
        if (playerCard == Card.EMPEROR && outcome == Outcome.LOSE) return "AI";
        if (aiCard == Card.EMPEROR && outcome == Outcome.WIN) return "PLAYER";
        return null;
    }

    public synchronized Map<String, Object> statePayload(String type) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", type);
        payload.put("round", round);
        payload.put("playerScore", playerScore);
        payload.put("opponentScore", aiScore);
        payload.put("playerRemaining", playerDeck.snapshot());
        payload.put("opponentRemaining", aiDeck.snapshot());
        if (lastMadmanEffect != null) {
            payload.put("madmanEffect", lastMadmanEffect);
        }
        return payload;
    }
}
