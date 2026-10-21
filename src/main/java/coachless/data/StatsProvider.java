package coachless.data;

import java.util.Map;

public interface StatsProvider {
    Map<String, Double> load();
}
