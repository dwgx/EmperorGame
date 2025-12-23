package work.emperor.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Deck {
    public record DeckConfig(int emperor, int citizen, int slave, int traitor, int madman) {
        public static DeckConfig standard() { return new DeckConfig(1, 4, 1, 1, 1); }
        public static DeckConfig randomPreset() {
            var r = ThreadLocalRandom.current();
            int emperor = 1; // 固定
            int citizen = 4 + r.nextInt(4); // 4-7
            int slave = 1 + r.nextInt(2);   // 1-2
            int traitor = 1 + r.nextInt(2); // 1-2
            int madman = 1 + r.nextInt(2);  // 1-2
            return new DeckConfig(emperor, citizen, slave, traitor, madman);
        }
    }

    private int emperor;
    private int citizen;
    private int slave;
    private int traitor;
    private int madman;

    public static Deck fresh() {
        return new Deck(DeckConfig.standard());
    }

    public Deck(DeckConfig config) {
        this.emperor = config.emperor();
        this.citizen = config.citizen();
        this.slave = config.slave();
        this.traitor = config.traitor();
        this.madman = config.madman();
    }

    public Map<String, Integer> snapshot() {
        Map<String, Integer> map = new HashMap<>();
        map.put("emperor", emperor);
        map.put("citizen", citizen);
        map.put("slave", slave);
        map.put("traitor", traitor);
        map.put("madman", madman);
        return map;
    }

    public int remaining() {
        return emperor + citizen + slave + traitor + madman;
    }

    public boolean hasCard(Card c) {
        return switch (c) {
            case EMPEROR -> emperor > 0;
            case CITIZEN -> citizen > 0;
            case SLAVE -> slave > 0;
            case TRAITOR -> traitor > 0;
            case MADMAN -> madman > 0;
        };
    }

    public void use(Card c) {
        switch (c) {
            case EMPEROR -> {
                if (emperor == 0) throw new IllegalStateException("No emperor cards remaining");
                emperor--;
            }
            case TRAITOR -> {
                if (traitor == 0) throw new IllegalStateException("No traitor cards remaining");
                traitor--;
            }
            case MADMAN -> {
                if (madman == 0) throw new IllegalStateException("No madman cards remaining");
                madman--;
            }
            case CITIZEN -> {
                if (citizen == 0) throw new IllegalStateException("No citizen cards remaining");
                citizen--;
            }
            case SLAVE -> {
                if (slave == 0) throw new IllegalStateException("No slave cards remaining");
                slave--;
            }
        }
    }

    public Card drawRandom() {
        int total = remaining();
        if (total == 0) throw new IllegalStateException("No cards remaining");
        int pick = ThreadLocalRandom.current().nextInt(total);
        return drawRandomInternal(pick);
    }

    public Card drawRandomRemaining() {
        int total = remaining();
        if (total == 0) return null;
        int pick = ThreadLocalRandom.current().nextInt(total);
        return drawRandomInternal(pick);
    }

    public void putBack(Card c) {
        if (c == null) return;
        switch (c) {
            case EMPEROR -> emperor++;
            case CITIZEN -> citizen++;
            case SLAVE -> slave++;
            case TRAITOR -> traitor++;
            case MADMAN -> madman++;
        }
    }

    private Card drawRandomInternal(int pick) {
        if (pick < emperor) {
            emperor--;
            return Card.EMPEROR;
        }
        pick -= emperor;
        if (pick < citizen) {
            citizen--;
            return Card.CITIZEN;
        }
        pick -= citizen;
        if (pick < slave) {
            slave--;
            return Card.SLAVE;
        }
        pick -= slave;
        if (pick < traitor) {
            traitor--;
            return Card.TRAITOR;
        }
        madman--;
        return Card.MADMAN;
    }
}
