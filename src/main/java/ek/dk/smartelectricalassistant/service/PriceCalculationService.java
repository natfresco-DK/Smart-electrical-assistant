package ek.dk.smartelectricalassistant.service;

import ek.dk.smartelectricalassistant.dto.Records;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

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
}
