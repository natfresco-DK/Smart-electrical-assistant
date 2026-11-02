package ek.dk.smartelectricalassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class Records{

    public record AdviceResponse(
            String summary,
            List<String> reasons,
            String recommendation
    ){}

    public record AdviceRequest(String prompt){}

    public record ElectricityPrice(
            @JsonProperty("DKK_per_kWh") BigDecimal dkkPerKwh,
            @JsonProperty("time_start") OffsetDateTime timeStart,
            @JsonProperty("time_end") OffsetDateTime timeEnd
    ){}

    public record PriceWindow(){}
}

