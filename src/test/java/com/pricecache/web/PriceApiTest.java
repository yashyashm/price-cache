package com.pricecache.web;

import com.pricecache.repository.InMemoryPriceRepository;
import com.pricecache.service.InMemoryPriceSubscriptions;
import com.pricecache.service.PriceService;
import com.pricecache.service.PriceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class PriceApiTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PriceService service = new PriceServiceImpl(new InMemoryPriceRepository(),
                new InMemoryPriceSubscriptions(Runnable::run, Duration.ofSeconds(5), 3),
                Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC), Duration.ofDays(30));
        mvc = MockMvcBuilders.standaloneSetup(new PriceController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void publishAndRetrieveThroughBothEndpoints() throws Exception {
        publish(body("V1", "AAPL", "100", "2026-11-09T11:00:00Z"));
        publish(body("V2", "AAPL", "105", "2026-11-09T11:00:00Z"));
        publish(body("V1", "MSFT", "200", "2026-11-09T11:00:00Z"));

        mvc.perform(get("/api/prices/vendors/V1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].instrument").value("AAPL"));

        mvc.perform(get("/api/prices/instruments/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].vendor").value("V2"));

        mvc.perform(get("/api/prices/vendors/UNKNOWN"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void rejectsExpiredAndFutureQuotesAndOverwritesSameTimeValue() throws Exception {
        mvc.perform(post("/api/prices").contentType(MediaType.APPLICATION_JSON)
                        .content(body("V1", "AAPL", "100", "2026-10-01T00:00:00Z")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(post("/api/prices").contentType(MediaType.APPLICATION_JSON)
                        .content(body("V1", "AAPL", "100", "2026-11-10T00:00:00Z")))
                .andExpect(status().isBadRequest());
        publish(body("V1", "AAPL", "100", "2026-11-09T11:00:00Z"));
        mvc.perform(post("/api/prices").contentType(MediaType.APPLICATION_JSON)
                        .content(body("V1", "AAPL", "99", "2026-11-09T11:00:00Z")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/prices/instruments/AAPL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bid").value(99));
    }


    @Test
    void asOfParameterSelectsThePointInTimeOtherwiseLatest() throws Exception {
        publish(body("V1", "AAPL", "100", "2026-11-09T09:00:00Z"));
        publish(body("V1", "AAPL", "110", "2026-11-09T11:00:00Z"));

        mvc.perform(get("/api/prices/instruments/AAPL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bid").value(110));

        mvc.perform(get("/api/prices/instruments/AAPL").param("asOf", "2026-11-09T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bid").value(100));

        mvc.perform(get("/api/prices/instruments/AAPL").param("asOf", "2026-11-08T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        mvc.perform(get("/api/prices/instruments/AAPL").param("asOf", "not-a-time"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void malformedAndIncompleteBodiesReturnClientErrors() throws Exception {
        for (String invalid : new String[] {
                "{", "{}", body(null, "AAPL", "100", "2026-11-09T11:00:00Z"),
                body("V1", "AAPL", "100", "not-a-date"),
                body("V1", "AAPL", "100", "2026-11-09T11:00:00Z").replace("USD", "INVALID")
        }) {
            mvc.perform(post("/api/prices").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        }
    }

    private void publish(String body) throws Exception {
        mvc.perform(post("/api/prices").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.observedAt").isString());
    }

    private String body(String vendor, String instrument, String price, String timestamp) {
        return """
            {"vendor":%s,"instrument":%s,"bid":%s,"ask":%s,"currency":"USD","observedAt":%s}
            """.formatted(jsonString(vendor), jsonString(instrument), price, price, jsonString(timestamp));
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}