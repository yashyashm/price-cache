package com.pricecache.web;


import java.math.BigDecimal;
import java.time.Instant;

import com.pricecache.domain.Price;
import com.pricecache.service.InMemoryPriceSubscriptions;
import com.pricecache.service.PriceSubscriptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;


class SubscriptionControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private PriceSubscriptions subscriptions;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        subscriptions = new InMemoryPriceSubscriptions(Runnable::run, java.time.Duration.ofSeconds(5), 3);
        mvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscriptions, 30_000L)).build();
    }

    @Test
    void vendorStreamOpensAndReceivesMatchingQuotesAsEvents() throws Exception {
        MvcResult result = mvc.perform(get("/api/subscriptions/vendors/V1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        subscriptions.distribute(quote("V1", "AAPL"));

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("event:price"), () -> "expected an SSE price event but got: " + body);
        assertTrue(body.contains("\"vendor\":\"V1\""), () -> "event data should carry the quote: " + body);
    }

    @Test
    void instrumentStreamOnlyReceivesMatchingInstrument() throws Exception {
        MvcResult result = mvc.perform(get("/api/subscriptions/instruments/AAPL")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        subscriptions.distribute(quote("V1", "MSFT"));
        assertEquals("", result.getResponse().getContentAsString(), "A non-matching instrument must not be streamed");

        subscriptions.distribute(quote("V1", "AAPL"));
        assertTrue(result.getResponse().getContentAsString().contains("\"instrument\":\"AAPL\""));
    }

    @Test
    void streamOpensEvenWhenNoQuotesHaveArrivedYet() throws Exception {
        mvc.perform(get("/api/subscriptions/vendors/UNKNOWN").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    private static Price quote(String vendor, String instrument) {
        return new Price(vendor, instrument, new BigDecimal("100.00"), new BigDecimal("100.10"), "USD", NOW);
    }
}