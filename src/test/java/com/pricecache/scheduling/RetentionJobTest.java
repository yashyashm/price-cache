package com.pricecache.scheduling;

import com.pricecache.service.PriceService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RetentionJobTest {
    @Test
    void cleanupDelegatesToRemoveExpired() {
        PriceService service = mock(PriceService.class);
        when(service.removeExpired()).thenReturn(5);

        new RetentionJob(service).cleanup();

        verify(service).removeExpired();
        verifyNoMoreInteractions(service);
    }

    @Test
    void cleanupRunsWhenNothingIsExpired() {
        PriceService service = mock(PriceService.class);
        when(service.removeExpired()).thenReturn(0);

        new RetentionJob(service).cleanup();

        verify(service).removeExpired();
    }
}