package io.github.hjle.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;

    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "dailySettlement", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void dailySettlement() {
        log.info("Daily settlement started");
        settlementService.runSettlement();
        log.info("Daily settlement completed");
    }
}
