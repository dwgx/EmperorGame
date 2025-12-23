package work.emperor.manager;

public enum Outcome {
    WIN,
    LOSE,
    DRAW;

    public static Outcome versus(Card a, Card b) {
        // 疯子：强制平局
        if (a == Card.MADMAN || b == Card.MADMAN) return DRAW;
        // 完全同牌：平局
        if (a == b) return DRAW;
        // 叛徒：只盯皇帝，其他都输
        if (a == Card.TRAITOR) return (b == Card.EMPEROR) ? WIN : LOSE;
        if (b == Card.TRAITOR) return (a == Card.EMPEROR) ? LOSE : WIN;
        // 标准克制：皇帝 > 平民 > 奴隶 > 皇帝
        boolean aWins = (a == Card.EMPEROR && b == Card.CITIZEN)
                || (a == Card.CITIZEN && b == Card.SLAVE)
                || (a == Card.SLAVE && b == Card.EMPEROR);
        return aWins ? WIN : LOSE;
    }
}
