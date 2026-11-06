package ek.dk.smartelectricalassistant.service;

import ek.dk.smartelectricalassistant.dto.Records;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PriceCalculationService {
    private final Clock clock;

    public PriceCalculationService(Clock clock) {
        this.clock = clock;
    }

    public List<Records.PriceWindow> findValidWindows(List<Records.ElectricityPrice> electricityPriceList,
                                                      int duration,
                                                      OffsetDateTime earliestStartTime,
                                                      OffsetDateTime latestEndTime) {
        if (duration <= 0 || duration > 4) {
            throw new IllegalArgumentException("Duration must be greater than zero and maximum 4");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (earliestStartTime == null || earliestStartTime.isBefore(now)) {
            earliestStartTime = electricityPriceList.stream()
                    .map(Records.ElectricityPrice::timeStart)
                    .filter(timeStart -> !timeStart.isBefore(now))
                    .findFirst().orElse(null);
        }
        if(earliestStartTime == null){
            return List.of();
        }
        if (latestEndTime == null) {
            latestEndTime = now.with(LocalTime.MAX);
        }

        List<Records.PriceWindow> validWindows = new ArrayList<>();
        for (int startIndex = 0; startIndex + duration <= electricityPriceList.size(); startIndex++) {
            Records.ElectricityPrice firstPrice = electricityPriceList.get(startIndex);
            Records.ElectricityPrice lastPrice = electricityPriceList.get(startIndex + duration - 1);

            OffsetDateTime windowStart = firstPrice.timeStart();
            OffsetDateTime windowEnd = lastPrice.timeEnd();

            boolean startsEarlyEnough = !windowStart.isBefore(earliestStartTime);
            boolean endsLateEnough = !windowEnd.isAfter(latestEndTime);

            if (!startsEarlyEnough || !endsLateEnough) {
                continue;
            }

            BigDecimal totalPrice = BigDecimal.ZERO;
            for (int index = startIndex; index < startIndex + duration; index++) {
                totalPrice = totalPrice.add(electricityPriceList.get(index).dkkPerKwh());
            }

            BigDecimal avgPrice = totalPrice.divide(BigDecimal.valueOf(duration), 5, RoundingMode.HALF_UP);

            String candidateId = "candidate-" + validWindows.size(); // Generate a unique candidate ID for each valid window

            validWindows.add(new Records.PriceWindow(candidateId, windowStart, windowEnd, avgPrice));
        }

        return validWindows;
    }

    public Records.PriceAnalysis analyze(List<Records.PriceWindow> candidates, BigDecimal estimatedKwh) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates cannot be null or empty");
        }
        if (estimatedKwh == null
                || estimatedKwh.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Estimated kWh must be greater than zero");
        }
        Set<String> candidateIds = new HashSet<>();
        List<Records.PricedWindow> pricedCandidates = new ArrayList<>();
        for (Records.PriceWindow candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("Candidates cannot contain null values");
            }
            if (candidate.candidateId() == null
                    || candidate.candidateId().isBlank()) {
                throw new IllegalArgumentException("Every candidate must have an ID");
            }
            if (!candidateIds.add(candidate.candidateId())) {
                throw new IllegalArgumentException("Candidate IDs must be unique");
            }
            if (candidate.startTime() == null || candidate.endTime() == null || candidate.averageSpotPrice() == null) {
                throw new IllegalArgumentException("Candidate fields cannot be null");
            }
            BigDecimal estimatedCost = candidate.averageSpotPrice()
                    .multiply(estimatedKwh)
                    .setScale(2, RoundingMode.HALF_UP);
            pricedCandidates.add(new Records.PricedWindow(
                    candidate.candidateId(),
                    candidate,
                    estimatedCost
            ));
        }
        Records.PricedWindow cheapestWindow = pricedCandidates.stream()
                .min(Comparator
                        .comparing(Records.PricedWindow::estimatedCost)
                        .thenComparing(window ->
                                window.priceWindow().startTime()))
                .orElseThrow();
        Records.PricedWindow baselineWindow = pricedCandidates.stream()
                .min(Comparator.comparing(window ->
                        window.priceWindow().startTime()))
                .orElseThrow();
        return new Records.PriceAnalysis(pricedCandidates, cheapestWindow, baselineWindow
        );
    }
}
