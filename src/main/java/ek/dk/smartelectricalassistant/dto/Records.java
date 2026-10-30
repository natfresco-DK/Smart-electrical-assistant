package ek.dk.smartelectricalassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class Records{

    public record ElectricityAdvice(
            String summary,
            List<String> reasons,
            String recommendation
    ) {}

    public record geminiRequest(String prompt){}

    public record ElectricityPrice(
            @JsonProperty("DKK_per_kWh") BigDecimal dkkPerKwh,
            @JsonProperty("EUR_per_kWh") BigDecimal eurPerKwh,
            @JsonProperty("EXR") BigDecimal exchangeRate,
            @JsonProperty("time_start") OffsetDateTime timeStart,
            @JsonProperty("time_end") OffsetDateTime timeEnd
    ) {
    }
}

