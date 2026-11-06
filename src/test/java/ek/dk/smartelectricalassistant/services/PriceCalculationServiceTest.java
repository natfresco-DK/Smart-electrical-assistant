package ek.dk.smartelectricalassistant.services;

import ek.dk.smartelectricalassistant.dto.Records;
import ek.dk.smartelectricalassistant.service.PriceCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PriceCalculationServiceTest {
    //it is always 17:30
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-03T17:30:00+01:00");

    private PriceCalculationService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                NOW.toInstant(),
                NOW.getOffset()
        );

        service = new PriceCalculationService(fixedClock);
    }

    //helpers
    //Price record creater
    private Records.ElectricityPrice price(
            int startHour,
            String dkkPerKwh) {
        OffsetDateTime start = OffsetDateTime.of(
                2026, 11, 3,
                startHour, 0, 0, 0,
                ZoneOffset.ofHours(1));
        return new Records.ElectricityPrice(
                new BigDecimal(dkkPerKwh),
                start,
                start.plusHours(1));
    }

    public void assertWindow(
            Records.PriceWindow actual,
            int expectedStartHour,
            int expectedEndHour,
            String expectedAveragePrice) {
        assertEquals(expectedStartHour, actual.startTime().getHour());
        assertEquals(expectedEndHour, actual.endTime().getHour());
        assertEquals(0, actual.averageSpotPrice().compareTo(new BigDecimal(expectedAveragePrice)));
    }
    //Find valid windows tests
    @Test
    void returnEveryValidCnectutiveWindow() {
        List<Records.ElectricityPrice> prices = List.of(
                price(18, "10.00"),
                price(19, "20.00"),
                price(20, "30.00"),
                price(21, "40.00")
        );

        List<Records.PriceWindow> result = service.findValidWindows(prices, 2, null, null);

        assertEquals(3, result.size());

        assertWindow(result.get(0), 18, 20, "15.00");
        assertWindow(result.get(1), 19, 21, "25.00");
        assertWindow(result.get(2), 20, 22, "35.00");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void acceptDurationsFromOneToFourHours(int duration) {
        List<Records.ElectricityPrice> prices = List.of(
                price(18, "10.00"),
                price(19, "20.00"),
                price(20, "30.00"),
                price(21, "40.00"),
                price(22, "50.00")
        );

        List<Records.PriceWindow> result =
                service.findValidWindows(prices, duration, null, null);

        assertFalse(result.isEmpty());

        for (Records.PriceWindow window : result) {
            long hours = Duration.between(
                    window.startTime(),
                    window.endTime()).toHours();
            assertEquals(duration, hours);
        }
    }

    @Test
    void rejectsDurationsGreaterThanFourHours() {
        List<Records.ElectricityPrice> prices = List.of(price(18, "10.00"));

        assertThrows(IllegalArgumentException.class, () -> service.findValidWindows(prices, 0, null, null));
    }

    @Test
    void rejectsZeroDuration() {
        List<Records.ElectricityPrice> prices = List.of(price(18, "10.00"));

        assertThrows(IllegalArgumentException.class, () -> service.findValidWindows(prices, 0, null, null));
    }

    @Test
    void respectsEarliestStartTime() {
        List<Records.ElectricityPrice> prices = List.of(
                price(18, "10.00"),
                price(19, "20.00"),
                price(20, "30.00"),
                price(21, "40.00")
        );

        OffsetDateTime earliestStart = OffsetDateTime.parse("2026-11-03T19:00:00+01:00");

        List<Records.PriceWindow> result = service.findValidWindows(prices, 1, earliestStart, null);

        assertEquals(3, result.size());
        assertEquals(19, result.get(0).startTime().getHour());
    }

    @Test
    void respectsLatestFinishTime() {
        List<Records.ElectricityPrice> prices = List.of(
                price(18, "10.00"),
                price(19, "20.00"),
                price(20, "30.00"),
                price(21, "40.00")
        );
        OffsetDateTime latestFinish = OffsetDateTime.parse("2026-11-03T21:00:00+01:00");

        List<Records.PriceWindow> result = service.findValidWindows(prices, 1, null, latestFinish);

        assertEquals(3, result.size());
        for (Records.PriceWindow priceWindow : result) {
            assertFalse(priceWindow.endTime().isAfter(latestFinish));
        }
    }

    @Test
    void excludesWindowsThatStartInThePast() {
        List<Records.ElectricityPrice> prices = List.of(
                price(17, "10.00"),
                price(18, "20.00"),
                price(19, "30.00")
        );

        List<Records.PriceWindow> result =
                service.findValidWindows(prices, 1, null, null);

        assertEquals(2, result.size());
        assertEquals(18, result.get(0).startTime().getHour());
        assertEquals(19, result.get(1).startTime().getHour());
    }

    @Test
    void calculatesAverageSpotPrice() {
        List<Records.ElectricityPrice> prices = List.of(
                price(18, "10.00"),
                price(19, "20.00"),
                price(20, "30.00")
        );

        List<Records.PriceWindow> result =
                service.findValidWindows(prices, 3, null, null);

        assertEquals(1, result.size());
        assertWindow(result.get(0), 18, 21, "20.00");
    }

    @Test
    void returnsNoCandidatesWhenInsufficientTimeRemains() {
        List<Records.ElectricityPrice> prices = List.of(
                price(17, "10.00"),
                price(18, "20.00")
        );

        List<Records.PriceWindow> result =
                service.findValidWindows(prices, 2, null, null);

        assertTrue(result.isEmpty());
    }

    //analyze tests
    @Test
    void calculatesCostForEveryCandidateAndPreservesCandidateIds() {
        Records.PriceWindow firstWindow = window(18, "1.50");
        Records.PriceWindow secondWindow = window(20, "0.75");
        Map<String, Records.PriceWindow> candidates = new LinkedHashMap<>();
        candidates.put("candidate-1", firstWindow);
        candidates.put("candidate-2", secondWindow);

        Records.PriceAnalysis result = service.analyze(candidates, new BigDecimal("2.0"));

        assertEquals(2, result.validCandidates().size());
        assertEquals("candidate-1", result.validCandidates().get(0).candidateId());
        assertSame(firstWindow, result.validCandidates().get(0).priceWindow());
        assertEquals(new BigDecimal("3.00"), result.validCandidates().get(0).estimatedCost());
        assertEquals("candidate-2", result.validCandidates().get(1).candidateId());
        assertEquals(new BigDecimal("1.50"), result.validCandidates().get(1).estimatedCost());
    }

    @Test
    void selectsCheapestWindowAndEarliestWindowAsBaseline() {
        Records.PriceWindow lateWindow = window(20, "0.50");
        Records.PriceWindow earliestWindow = window(18, "1.00");
        Records.PriceWindow middleWindow = window(19, "2.00");
        Map<String, Records.PriceWindow> candidates = new LinkedHashMap<>();
        candidates.put("late", lateWindow);
        candidates.put("earliest", earliestWindow);
        candidates.put("middle", middleWindow);

        Records.PriceAnalysis result = service.analyze(candidates, new BigDecimal("2"));

        assertEquals("late", result.cheapestWindow().candidateId());
        assertEquals("earliest", result.baselineWindow().candidateId());
    }

    @Test
    void resolvesEqualCostsByChoosingTheEarliestWindow() {
        Map<String, Records.PriceWindow> candidates = new LinkedHashMap<>();
        candidates.put("late", window(20, "1.00"));
        candidates.put("early", window(18, "1.00"));

        Records.PriceAnalysis result = service.analyze(candidates, new BigDecimal("2"));

        assertEquals("early", result.cheapestWindow().candidateId());
    }

    @Test
    void supportsNegativeSpotPrices() {
        Map<String, Records.PriceWindow> candidates = new LinkedHashMap<>();
        candidates.put("positive", window(18, "0.25"));
        candidates.put("negative", window(19, "-0.50"));

        Records.PriceAnalysis result = service.analyze(candidates, new BigDecimal("2"));

        assertEquals(new BigDecimal("-1.00"), result.validCandidates().get(1).estimatedCost());
        assertEquals("negative", result.cheapestWindow().candidateId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.0"})
    void rejectsNonPositiveEstimatedConsumption(String estimatedKwh) {
        Map<String, Records.PriceWindow> candidates =
                Map.of("candidate-1", window(18, "1.50"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze(candidates, new BigDecimal(estimatedKwh)));
    }

    @Test
    void rejectsNullEstimatedConsumption() {
        Map<String, Records.PriceWindow> candidates =
                Map.of("candidate-1", window(18, "1.50"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze(candidates, null));
    }

    @Test
    void rejectsNullAndEmptyCandidates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze(null, BigDecimal.ONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze(Map.of(), BigDecimal.ONE));
    }

    private Records.PriceWindow window(int startHour, String averageSpotPrice) {
        OffsetDateTime start = OffsetDateTime.of(
                2026, 11, 3,
                startHour, 0, 0, 0,
                ZoneOffset.ofHours(1));
        return new Records.PriceWindow(
                start,
                start.plusHours(1),
                new BigDecimal(averageSpotPrice));
    }
}