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
import java.util.List;

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
}