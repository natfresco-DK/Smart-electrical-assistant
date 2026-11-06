package ek.dk.smartelectricalassistant.controllers;

import ek.dk.smartelectricalassistant.dto.Records;
import ek.dk.smartelectricalassistant.model.PriceArea;
import ek.dk.smartelectricalassistant.service.AdviceService;
import ek.dk.smartelectricalassistant.service.ElectricityPriceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/electricity-prices")
public class UserController {

    private final ElectricityPriceService electricityPriceService;
    private final AdviceService adviceService;

    public UserController(ElectricityPriceService electricityPriceService, AdviceService adviceService) {
        this.electricityPriceService = electricityPriceService;
        this.adviceService = adviceService;
    }

    @GetMapping
    public List<Records.ElectricityPrice> getPrices(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "DK2") PriceArea priceArea) {
        return electricityPriceService.getPrices(date, priceArea);
    }

    @PostMapping
    public Records.AdviceResponse getAdvice(@RequestBody Records.AdviceRequest request) {
        return adviceService.getAdvice(request);
    }
}
