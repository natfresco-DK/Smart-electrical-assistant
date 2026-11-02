package ek.dk.smartelectricalassistant.controllers;

import ek.dk.smartelectricalassistant.dto.Records;
import ek.dk.smartelectricalassistant.service.ElectricityPriceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/electricity-prices")
public class UserController {

    private final ElectricityPriceService electricityPriceService;

    public UserController(ElectricityPriceService electricityPriceService) {
        this.electricityPriceService = electricityPriceService;
    }

    @GetMapping
    public List<Records.ElectricityPrice> getPrices(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "DK2") String zone) {
        return electricityPriceService.getPrices(date, zone);
    }
}
