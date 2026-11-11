package com.pricecache;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.pricecache.repository.CompositePriceRepository;
import com.pricecache.repository.InMemoryPriceRepository;
import com.pricecache.repository.KafkaPriceRepository;
import com.pricecache.repository.PriceRepository;
import com.pricecache.service.InMemoryPriceSubscriptions;
import com.pricecache.service.PriceService;
import com.pricecache.service.PriceServiceImpl;
import com.pricecache.service.PriceSubscriptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class PriceCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(PriceCacheApplication.class, args);
    }

    @Bean
    PriceRepository priceRepository() {
        return new CompositePriceRepository(new InMemoryPriceRepository(),
                List.of(new KafkaPriceRepository()));
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService distributionExecutorService(@Value("${distribution.executor.threads:10}") int nThreads) {
        return Executors.newFixedThreadPool(nThreads);
    }

    @Bean
    PriceSubscriptions priceSubscriptions(ExecutorService distributionExecutorService,
                                          @Value("${prices.distribution.timeout-ms:5000}") long distributionTimeoutMs,
                                          @Value("${prices.distribution.max-consecutive-failures:3}") int maxConsecutiveFailures) {
        return new InMemoryPriceSubscriptions(distributionExecutorService, Duration.ofMillis(distributionTimeoutMs),
                maxConsecutiveFailures);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PriceService priceService(PriceRepository priceRepository, PriceSubscriptions priceSubscriptions, Clock clock,
                              @Value("${prices.retention-days:30}") long retentionDays) {
        return new PriceServiceImpl(priceRepository, priceSubscriptions, clock, Duration.ofDays(retentionDays));
    }
}