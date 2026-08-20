package com.hbelange.financebudgetapp.demo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/**
 * Verifies the app.demo.enabled gate at the property-check-logic level. Actual cron firing
 * timing is Spring's responsibility, not ours to test here.
 */
class DemoResetSchedulerTest {

    @Test
    void seedOnStartup_callsReset_whenDemoEnabled() {
        DemoDataService demoDataService = mock(DemoDataService.class);
        DemoResetScheduler scheduler = new DemoResetScheduler(demoDataService, true);

        scheduler.seedOnStartup();

        verify(demoDataService).reset();
    }

    @Test
    void seedOnStartup_doesNotCallReset_whenDemoDisabled() {
        DemoDataService demoDataService = mock(DemoDataService.class);
        DemoResetScheduler scheduler = new DemoResetScheduler(demoDataService, false);

        scheduler.seedOnStartup();

        verify(demoDataService, never()).reset();
    }

    @Test
    void resetHourly_callsReset_whenDemoEnabled() {
        DemoDataService demoDataService = mock(DemoDataService.class);
        DemoResetScheduler scheduler = new DemoResetScheduler(demoDataService, true);

        scheduler.resetHourly();

        verify(demoDataService).reset();
    }

    @Test
    void resetHourly_doesNotCallReset_whenDemoDisabled() {
        DemoDataService demoDataService = mock(DemoDataService.class);
        DemoResetScheduler scheduler = new DemoResetScheduler(demoDataService, false);

        scheduler.resetHourly();

        verify(demoDataService, never()).reset();
    }
}
