package coachless.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PersonalStats implements StatsProvider {
    private final SaveFileManager saves;
 
    public PersonalStats(SaveFileManager saves) { this.saves = saves; }

    @Override
    public Map<String, Double> load() {
        Map<String, Double> m = new LinkedHashMap<>();
        List<ProgressSave> all = saves.validSaves();
 
        long wins = 0, runs = 0, floors = 0, seconds = 0, streak = 0;
        for (ProgressSave s : all) {
            wins += s.wins;
            runs += s.runs();
            floors += s.floorsClimbed;
            seconds += s.playtimeSeconds;
            streak = Math.max(streak, s.bestWinStreak);
        }
        if (runs == 0) return m;   // nothing loaded yet -> ComparePanel shows a hint
 
        m.put("Win rate %", 100.0 * wins / runs);
        m.put("Avg floors reached", (double) floors / runs);
        m.put("Avg run time", seconds / 60.0 / runs);
        m.put("Best win streak", (double) streak);
        return m;
    }
    
    @Override
    public void addChangeListener(Runnable listener) {
        saves.addListener(listener);
    }
}
