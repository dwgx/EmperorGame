package work.emperor.manager;

import work.emperor.model.PlayerContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class GameRoom {
    private final String id;
    public final PlayerContext a;
    public final PlayerContext b;

    private final Deck deckA;
    private final Deck deckB;

    private int scoreA = 0;
    private int scoreB = 0;
    private int round = 1;

    private Card pendingA = null;
    private Card pendingB = null;
    private boolean publicRound = false;
    private String lastMadmanEffect = null;

    public GameRoom(String id, PlayerContext a, PlayerContext b, Deck.DeckConfig config) {
        this.id = id;
        this.a = a;
        this.b = b;
        this.deckA = new Deck(config);
        this.deckB = new Deck(config);
    }

    public String getId() {
        return id;
    }

    public PlayerContext opponentOf(PlayerContext ctx) {
        if (ctx == a) return b;
        if (ctx == b) return a;
        return null;
    }

    public PlayerContext getPlayerA() {
        return a;
    }

    public PlayerContext getPlayerB() {
        return b;
    }

    public Map<String, Object> matchPayloadFor(PlayerContext viewer) {
        PlayerContext opponent = opponentOf(viewer);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "match_found");
        payload.put("roomId", id);
        payload.put("opponent", Map.of(
                "sessionId", opponent.sessionId,
                "nickname", opponent.nickname
        ));
        payload.put("you", Map.of(
                "sessionId", viewer.sessionId,
                "nickname", viewer.nickname
        ));
        payload.put("scores", Map.of(
                a.sessionId, scoreA,
                b.sessionId, scoreB
        ));
        payload.put("remaining", Map.of(
                a.sessionId, deckA.snapshot(),
                b.sessionId, deckB.snapshot()
        ));
        payload.put("round", round);
        payload.put("publicRound", publicRound);
        payload.put("message", "匹配成功，开始出牌");
        return payload;
    }

    public synchronized PlayResult play(PlayerContext who, Card card) {
        if (isGameOver()) throw new IllegalStateException("本局已经结束");
        if (who != a && who != b) throw new IllegalStateException("不在这个房间");

        Deck deck = (who == a) ? deckA : deckB;
        if (!deck.hasCard(card)) throw new IllegalStateException("这张牌已经用完");

        if (who == a && pendingA != null) throw new IllegalStateException("你本回合已经出牌");
        if (who == b && pendingB != null) throw new IllegalStateException("你本回合已经出牌");

        deck.use(card);
        if (who == a) pendingA = card;
        else pendingB = card;

        PlayResult result = new PlayResult();
        if (publicRound) {
            result.revealBroadcast = Map.of(
                    "type", "card_revealed",
                    "roomId", id,
                    "round", round,
                    "sessionId", who.sessionId,
                    "card", card.name()
            );
        }

        if (pendingA == null || pendingB == null) {
            result.lockedAck = Map.of(
                    "type", "card_locked",
                    "roomId", id,
                    "round", round,
                    "message", "已出牌，等待对手"
            );
            return result;
        }

        MadmanEffectResult madman = maybeHandleMadman();

        Outcome outcome;
        if (madman != null && madman.redoRound) {
            outcome = Outcome.DRAW;
        } else {
            outcome = Outcome.versus(pendingA, pendingB);
            if (outcome == Outcome.WIN) scoreA++;
            if (outcome == Outcome.LOSE) scoreB++;
        }

        String forcedWinner = resolveForcedWinner(outcome);
        boolean nextRoundPublic = isPublicTrigger(pendingA) || isPublicTrigger(pendingB);
        boolean gameOver = forcedWinner != null || isGameOver();
        if (madman != null && madman.redoRound && forcedWinner == null) {
            // 重打时，如果牌已耗尽依然结束；否则继续同回合
            gameOver = isGameOver();
        }
        String finalResult = null;
        if (forcedWinner != null) {
            finalResult = forcedWinner;
            if (forcedWinner.equals(a.sessionId)) scoreA = Math.max(scoreA, scoreB + 1);
            else scoreB = Math.max(scoreB, scoreA + 1);
        } else if (gameOver) {
            if (scoreA == scoreB) finalResult = "DRAW";
            else finalResult = (scoreA > scoreB) ? a.sessionId : b.sessionId;
        }

        RoundBroadcast broadcast = new RoundBroadcast(
                id, round, gameOver,
                outcomePayload(outcome),
                finalResult,
                Map.of(
                        a.sessionId, pendingA.name(),
                        b.sessionId, pendingB.name()
                ),
                nextRoundPublic
        );
        broadcast.setMadmanEffect(lastMadmanEffect);
        broadcast.setScores(Map.of(a.sessionId, scoreA, b.sessionId, scoreB));
        broadcast.setRemaining(Map.of(a.sessionId, deckA.snapshot(), b.sessionId, deckB.snapshot()));

        pendingA = null;
        pendingB = null;
        publicRound = nextRoundPublic;
        if (madman != null && madman.redoRound) {
            // repeat same round index
        } else {
            round++;
        }

        result.roundBroadcast = broadcast;
        return result;
    }

    private boolean isGameOver() {
        return deckA.remaining() == 0 || deckB.remaining() == 0;
    }

    private String resolveForcedWinner(Outcome outcome) {
        // madman present: 不触发“一手定胜负”
        if (pendingA == Card.MADMAN || pendingB == Card.MADMAN) return null;
        // 叛徒只要抓到皇帝；奴隶打到皇帝；皇帝本手失败则整场失败
        if (pendingA == Card.TRAITOR && pendingB == Card.EMPEROR) return a.sessionId;
        if (pendingB == Card.TRAITOR && pendingA == Card.EMPEROR) return b.sessionId;
        if (pendingA == Card.SLAVE && pendingB == Card.EMPEROR) return a.sessionId;
        if (pendingB == Card.SLAVE && pendingA == Card.EMPEROR) return b.sessionId;
        if (pendingA == Card.EMPEROR && outcome == Outcome.LOSE) return b.sessionId;
        if (pendingB == Card.EMPEROR && outcome == Outcome.WIN) return a.sessionId;
        return null;
    }

    private MadmanEffectResult maybeHandleMadman() {
        if (pendingA != Card.MADMAN && pendingB != Card.MADMAN) {
            lastMadmanEffect = null;
            return null;
        }
        // 随机三选一：交换一张、各弃一张、重打（本手无效但疯子消耗）
        int roll = ThreadLocalRandom.current().nextInt(3);
        MadmanEffectResult res = new MadmanEffectResult();
        switch (roll) {
            case 0 -> {
                Card takeA = deckA.drawRandomRemaining();
                Card takeB = deckB.drawRandomRemaining();
                if (takeA != null && takeB != null) {
                    deckA.putBack(takeB);
                    deckB.putBack(takeA);
                } else {
                    res.effect = "no_effect";
                    break;
                }
                res.effect = "swap_random";
            }
            case 1 -> {
                Card dropA = deckA.drawRandomRemaining();
                Card dropB = deckB.drawRandomRemaining();
                if (dropA == null && dropB == null) {
                    res.effect = "no_effect";
                } else {
                    res.effect = "discard_random";
                }
            }
            default -> {
                // 本手作废：恢复除疯子外的牌，不计分，重打同一回合
                if (pendingA != Card.MADMAN) deckA.putBack(pendingA);
                if (pendingB != Card.MADMAN) deckB.putBack(pendingB);
                res.effect = "redo_round";
                res.redoRound = true;
            }
        }
        lastMadmanEffect = res.effect;
        return res;
    }

    private Deck deckOf(PlayerContext who) {
        return who == a ? deckA : deckB;
    }

    private Map<String, String> outcomePayload(Outcome outcome) {
        if (outcome == Outcome.DRAW) return Map.of("winner", "DRAW");
        return Map.of("winner", outcome == Outcome.WIN ? a.sessionId : b.sessionId);
    }

    private boolean isPublicTrigger(Card card) {
        return card == Card.TRAITOR || card == Card.MADMAN;
    }

    public static class PlayResult {
        public Map<String, Object> lockedAck;
        public Map<String, Object> revealBroadcast;
        public RoundBroadcast roundBroadcast;
    }

    private static class MadmanEffectResult {
        boolean redoRound = false;
        String effect;
    }
}
