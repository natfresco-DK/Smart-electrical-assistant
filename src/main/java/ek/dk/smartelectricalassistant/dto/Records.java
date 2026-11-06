package ek.dk.smartelectricalassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import ek.dk.smartelectricalassistant.model.MachineType;
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
            LocalTime earliestStart,
            LocalTime latestEnd,
            MachineType machineType
            ){
        public AdviceRequest {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question is required");
            }
            Objects.requireNonNull(region, "region is required");
            Objects.requireNonNull(machineType, "machineType is required");
            Objects.requireNonNull(duration, "duration is required");

            if (duration.compareTo(Duration.ofHours(1)) < 0
                    || duration.compareTo(Duration.ofHours(4)) > 0) {
                throw new IllegalArgumentException(
                        "duration must be between 1 and 4 hours");
            }
            if ((earliestStart == null) != (latestEnd == null)) {
                throw new IllegalArgumentException(
                        "earliestStart and latestEnd must be provided together");
            }
            if (earliestStart != null && Duration.between(earliestStart, latestEnd).compareTo(duration) < 0) {
                throw new IllegalArgumentException(
                        "latestEnd must be at least duration after earliestStart");
            }
        }
    }

    public record ElectricityPrice(
            @JsonProperty("DKK_per_kWh") BigDecimal dkkPerKwh,
            @JsonProperty("time_start") OffsetDateTime timeStart,
            @JsonProperty("time_end") OffsetDateTime timeEnd
    ){}

    public record PriceWindow(
            String candidateId,
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
            BigDecimal estimatedKwh,
            String selectedWindowId,
            String summary,
            List<String> reasons,
            List<String> assumptions
    ){}
}
