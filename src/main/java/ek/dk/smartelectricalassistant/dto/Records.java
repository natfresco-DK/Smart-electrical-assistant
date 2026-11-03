package ek.dk.smartelectricalassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import ek.dk.smartelectricalassistant.model.PriceArea;
import ek.dk.smartelectricalassistant.model.RecommendationSource;

public class Records{

    public record AdviceResponse(
            PriceArea priceArea,
            PricedWindow selectedWindow,
            PricedWindow cheapestWindow,
            AiWindowSelection explanation,
            BigDecimal extraCostVsCheapestDkk,
            RecommendationSource source,
            String priceNote
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

    public record PriceAnalysis(
            List<PricedWindow> validCandidates,
            PricedWindow cheapestWindow,
            PricedWindow baselineWindow
    ){}

    public record AiWindowSelection(
            String selectedCandidateId,
            String summary,
            List<String> reasons,
            List<String> assumptions
    ){}
}
