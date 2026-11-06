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

            validWindows.add(new Records.PriceWindow(windowStart, windowEnd, avgPrice));
        }

        return validWindows;
    }

    public Records.PriceAnalysis analyze(Map<String, Records.PriceWindow> candidates, BigDecimal estimatedKwh) {
        if (estimatedKwh == null || estimatedKwh.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Estimated kWh must be greater than zero");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("Candidates cannot be null");
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates cannot be empty");
        }
        List<Records.PricedWindow> validCandidates = new ArrayList<>();
        candidates.forEach((key, value) -> {
            BigDecimal estimatedCost = value.averageSpotPrice().multiply(estimatedKwh)
                    .setScale(2, RoundingMode.HALF_UP);
            validCandidates.add(new Records.PricedWindow(key, value, estimatedCost));
        });
       Records.PricedWindow cheapestWindow = validCandidates.stream()
        .min(
                Comparator.comparing(Records.PricedWindow::estimatedCost)
                .thenComparing(candidate ->
                        candidate.priceWindow().startTime()))
        .orElse(null);
        Records.PricedWindow baselineWindow = validCandidates.getFirst();
        for (Records.PricedWindow candidate : validCandidates) {
            if(candidate.priceWindow().startTime().isBefore(baselineWindow.priceWindow().startTime())) {
                baselineWindow = candidate;
            }
        }
        return new Records.PriceAnalysis(validCandidates, cheapestWindow, baselineWindow);
}
}
