package analytics.http;

import analytics.query.ActivityStats;
import analytics.query.StatsService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class StatsController {
    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    public ActivityStats current() {
        return stats.current();
    }
}
