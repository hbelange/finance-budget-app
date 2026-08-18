package com.hbelange.financebudgetapp.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the public demo account fresh: reseeds once at startup (so a deploy doesn't wait up to
 * an hour for data) and again every hour on the hour. Both actions are gated by
 * {@code app.demo.enabled} so local dev and the test suite never touch demo data.
 */
@Component
public class DemoResetScheduler {

    private final DemoDataService demoDataService;
    private final boolean demoEnabled;

    public DemoResetScheduler(DemoDataService demoDataService,
                               @Value("${app.demo.enabled:false}") boolean demoEnabled) {
        this.demoDataService = demoDataService;
        this.demoEnabled = demoEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (demoEnabled) {
            demoDataService.reset();
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void resetHourly() {
        if (demoEnabled) {
            demoDataService.reset();
        }
    }
}
