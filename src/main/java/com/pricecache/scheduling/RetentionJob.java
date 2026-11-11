package com.pricecache.scheduling;

import com.pricecache.service.PriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentionJob {
    private static final Logger LOG = LoggerFactory.getLogger(RetentionJob.class);
    private final PriceService service;

    public RetentionJob(PriceService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${prices.cleanup-delay-ms:60000}")
    public void cleanup() {
        int removed = service.removeExpired();
        if (removed > 0) {
            LOG.info("Removed {} expired prices", removed);
        }
    }
}
