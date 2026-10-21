package coachless.data;

import java.util.Map;

public final class ProgressSave {
    public final long wins;
    public final long losses;
    public final long floorsClimbed;
    public final long playtimeSeconds;
    public final long bestWinStreak;

    private ProgressSave(long wins, long losses, long floorsClimbed, long playtimeSeconds, long bestWinStreak) {
        this.wins = wins;
        this.losses = losses;
        this.floorsClimbed = floorsClimbed;
        this.playtimeSeconds = playtimeSeconds;
        this.bestWinStreak = bestWinStreak;
    }

    public long runs() { return wins + losses; }

    public static ProgressSave parse(String json) {
        Map<String, Object> root = Json.map(Json.parse(json));
        if (!root.containsKey("character_stats")) {
            throw new IllegalArgumentException("Not a progress.save file (no character_stats)");
        }

        long wins = 0, losses = 0, bestStreak = 0;
        for (Object o : Json.list(root.get("character_stats"))) {
            Map<String, Object> c = Json.map(o);
            if (!isPlayableCharacter(Json.str(c, "id", ""))) continue;
            wins += (long) Json.num(c, "total_wins", 0);
            losses += (long) Json.num(c, "total_losses", 0);
            bestStreak = Math.max(bestStreak, (long) Json.num(c, "best_win_streak", 0));
        }

        return new ProgressSave(
                wins, losses,
                (long) Json.num(root, "floors_climbed", 0),
                (long) Json.num(root, "total_playtime", 0),
                bestStreak);
    }

    // The five real characters
    private static boolean isPlayableCharacter(String id) {
        return id.startsWith("CHARACTER.")
                && !id.contains("-")
                && !id.equals("CHARACTER.RANDOM_CHARACTER");
    }
}
