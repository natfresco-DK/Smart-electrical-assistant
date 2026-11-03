package ek.dk.smartelectricalassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import ek.dk.smartelectricalassistant.model.PriceArea;

public class Records{

    public record AdviceResponse(
            String summary,
            List<String> reasons,
            String recommendation
    ){}

    public record AdviceRequest(
            String question,
            LocalDate date,
            PriceArea region,
            Duration duration,
            BigDecimal consumption,
            LocalTime earliestStart,
            LocalTime latestEnd
    ){}

    public record ElectricityPrice(
            @JsonProperty("DKK_per_kWh") BigDecimal dkkPerKwh,
            @JsonProperty("time_start") OffsetDateTime timeStart,
            @JsonProperty("time_end") OffsetDateTime timeEnd
    ){}

    public record PriceWindow(
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            BigDecimal averageSpotPrice
    ){}

    public record PricedWindow(
            String candidateId,
            PriceWindow priceWindow,
            BigDecimal estimatedCost
    ){}
}
