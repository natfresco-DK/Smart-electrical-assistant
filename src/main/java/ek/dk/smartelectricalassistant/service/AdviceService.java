package ek.dk.smartelectricalassistant.service;

import ek.dk.smartelectricalassistant.dto.Records;
import org.springframework.stereotype.Service;
import ek.dk.smartelectricalassistant.model.RecommendationSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

@Service
public class AdviceService {

    private final ElectricityPriceService electricityPriceService;
    private final PriceCalculationService priceCalculationService;
    private final AiAdviceService aiAdviceService;
    private final Clock clock;

    public AdviceService(
            ElectricityPriceService electricityPriceService,
            PriceCalculationService priceCalculationService,
            AiAdviceService aiAdviceService,
            Clock clock) {
        this.electricityPriceService = electricityPriceService;
        this.priceCalculationService = priceCalculationService;
        this.aiAdviceService = aiAdviceService;
        this.clock = clock;
    }

    public Records.AdviceResponse getAdvice(Records.AdviceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        Records.AdviceRequest resolvedRequest = resolveDate(request);
        int durationHours = toWholeHours(resolvedRequest.duration());
        List<Records.ElectricityPrice> prices =
                electricityPriceService.getPrices(
                        resolvedRequest.date(),
                        resolvedRequest.region());
        OffsetDateTime earliestStart =
                toDateTime(resolvedRequest.date(), resolvedRequest.earliestStart());
        OffsetDateTime latestEnd =
                toDateTime(resolvedRequest.date(), resolvedRequest.latestEnd());
        List<Records.PriceWindow> candidates =
                priceCalculationService.findValidWindows(
                        prices,
                        durationHours,
                        earliestStart,
                        latestEnd);
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No valid electricity-price windows were found");
        }
        Records.AiWindowSelection aiSelection = aiAdviceService.advise(resolvedRequest, candidates);
        validateEstimatedKwh(aiSelection);
        Records.PriceAnalysis analysis = priceCalculationService.analyze(
                        candidates,
                        aiSelection.estimatedKwh());

        Records.PricedWindow selectedWindow = findSelectedWindow(analysis, aiSelection.selectedWindowId());
        RecommendationSource source;
        if (selectedWindow == null) {
            selectedWindow = analysis.cheapestWindow();
            source = RecommendationSource.CHEAPEST_FALLBACK;
        } else {
            source = RecommendationSource.AI;
        }
        BigDecimal extraCostVsCheapestDkk =
                selectedWindow.estimatedCost()
                        .subtract(analysis.cheapestWindow().estimatedCost())
                        .setScale(2, RoundingMode.HALF_UP);
        String priceNote = source == RecommendationSource.CHEAPEST_FALLBACK
                ? "The AI selected an invalid candidate, so the cheapest valid window was used."
                : "Costs were calculated from the retrieved electricity prices.";
        return new Records.AdviceResponse(
                resolvedRequest.region(),
                selectedWindow,
                analysis.cheapestWindow(),
                aiSelection,
                extraCostVsCheapestDkk,
                source,
                priceNote);
    }

    private Records.AdviceRequest resolveDate(Records.AdviceRequest request) {
        LocalDate date = request.date() != null
                ? request.date()
                : LocalDate.now(clock);
        return new Records.AdviceRequest(
                request.question(),
                date,
                request.region(),
                request.duration(),
                request.earliestStart(),
                request.latestEnd(),
                request.machineType());
    }

    private int toWholeHours(Duration duration) {
        if (duration.toMinutes() % 60 != 0) {
            throw new IllegalArgumentException("duration must be a whole number of hours");
        }
        return (int) duration.toHours();
    }

    private OffsetDateTime toDateTime(LocalDate date, LocalTime time) {
        if (time == null) {
            return null;
        }
        return ZonedDateTime.of(
                date,
                time,
                clock.getZone())
                .toOffsetDateTime();
    }

    private void validateEstimatedKwh(Records.AiWindowSelection selection) {
        if (selection == null || selection.estimatedKwh() == null || selection.estimatedKwh().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI returned a missing or invalid electricity-consumption estimate"
            );
        }
    }

    private Records.PricedWindow findSelectedWindow(
            Records.PriceAnalysis analysis,
            String selectedWindowId) {
        if (selectedWindowId == null || selectedWindowId.isBlank()) {
            return null;
        }
        return analysis.validCandidates().stream()
                .filter(window -> window.candidateId().equals(selectedWindowId))
                .findFirst()
                .orElse(null);
    }
}