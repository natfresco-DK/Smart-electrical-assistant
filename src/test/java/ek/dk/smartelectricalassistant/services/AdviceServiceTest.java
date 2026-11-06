package ek.dk.smartelectricalassistant.services;

import ek.dk.smartelectricalassistant.dto.Records;
import ek.dk.smartelectricalassistant.model.MachineType;
import ek.dk.smartelectricalassistant.model.PriceArea;
import ek.dk.smartelectricalassistant.model.RecommendationSource;
import ek.dk.smartelectricalassistant.service.AdviceService;
import ek.dk.smartelectricalassistant.service.AiAdviceService;
import ek.dk.smartelectricalassistant.service.ElectricityPriceService;
import ek.dk.smartelectricalassistant.service.PriceCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class AdviceServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 11, 6, 17, 0, 0, 0, ZoneOffset.ofHours(1));

    private ElectricityPriceService electricityPriceService;
    private AiAdviceService aiAdviceService;
    private AdviceService service;

    @BeforeEach
    void setUp() {
        electricityPriceService = mock(ElectricityPriceService.class);
        aiAdviceService = mock(AiAdviceService.class);

        service = new AdviceService(
                electricityPriceService,
                new PriceCalculationService(Clock.fixed(NOW.toInstant(), NOW.getOffset())),
                aiAdviceService,
                Clock.fixed(NOW.toInstant(), NOW.getOffset()));

        when(electricityPriceService.getPrices(TODAY, PriceArea.DK2))
                .thenReturn(prices());
    }

    @Test
    void usesAiSelectedWindowAndCalculatesExtraCostInJava() {
        when(aiAdviceService.advise(any(), any()))
                .thenReturn(aiSelection("candidate-0", new BigDecimal("2")));

        Records.AdviceResponse response = service.getAdvice(request(TODAY));

        assertEquals(RecommendationSource.AI, response.source());
        assertEquals("candidate-0", response.selectedWindow().candidateId());
        assertEquals("candidate-1", response.cheapestWindow().candidateId());
        assertEquals(new BigDecimal("2.00"), response.extraCostVsCheapestDkk());
        verify(aiAdviceService, times(1)).advise(any(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "not-a-candidate"})
    void fallsBackToCheapestWindowForEveryInvalidCandidateId(String selectedWindowId) {
        when(aiAdviceService.advise(any(), any()))
                .thenReturn(aiSelection(selectedWindowId, new BigDecimal("2")));

        Records.AdviceResponse response = service.getAdvice(request(TODAY));

        assertEquals(RecommendationSource.CHEAPEST_FALLBACK, response.source());
        assertEquals("candidate-1", response.selectedWindow().candidateId());
        assertEquals(new BigDecimal("0.00"), response.extraCostVsCheapestDkk());
        verify(aiAdviceService, times(1)).advise(any(), any());
    }

    @Test
    void resolvesMissingDateBeforeFetchingPricesAndCallingAi() {
        when(aiAdviceService.advise(any(), any()))
                .thenReturn(aiSelection("candidate-1", new BigDecimal("2")));

        Records.AdviceRequest request = request(null);

        service.getAdvice(request);

        verify(electricityPriceService).getPrices(TODAY, PriceArea.DK2);
        verify(aiAdviceService).advise(
                argThat(resolved -> TODAY.equals(resolved.date())),
                any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsInvalidKwhBeforeCalculatingCosts(String estimatedKwh) {
        when(aiAdviceService.advise(any(), any()))
                .thenReturn(aiSelection("candidate-1", new BigDecimal(estimatedKwh)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getAdvice(request(TODAY)));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verify(aiAdviceService, times(1)).advise(any(), any());
    }

    @Test
    void rejectsMissingKwhBeforeCalculatingCosts() {
        when(aiAdviceService.advise(any(), any()))
                .thenReturn(aiSelection("candidate-1", null));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getAdvice(request(TODAY)));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verify(aiAdviceService, times(1)).advise(any(), any());
    }

    private Records.AdviceRequest request(LocalDate date) {
        return new Records.AdviceRequest(
                "Run the appliance at a cheap time.",
                date,
                PriceArea.DK2,
                Duration.ofHours(1),
                null,
                null,
                MachineType.DISHWASHER);
    }

    private Records.AiWindowSelection aiSelection(
            String selectedWindowId,
            BigDecimal estimatedKwh) {
        return new Records.AiWindowSelection(
                estimatedKwh,
                selectedWindowId,
                "The candidate fits the request.",
                List.of("The supplied prices were considered."),
                List.of("Typical consumption was assumed."));
    }

    private List<Records.ElectricityPrice> prices() {
        return List.of(
                price(18, "1.50"),
                price(19, "0.50"));
    }

    private Records.ElectricityPrice price(int hour, String dkkPerKwh) {
        OffsetDateTime start = OffsetDateTime.of(
                TODAY.getYear(),
                TODAY.getMonthValue(),
                TODAY.getDayOfMonth(),
                hour,
                0,
                0,
                0,
                ZoneOffset.ofHours(1));
        return new Records.ElectricityPrice(
                new BigDecimal(dkkPerKwh),
                start,
                start.plusHours(1));
    }
}
