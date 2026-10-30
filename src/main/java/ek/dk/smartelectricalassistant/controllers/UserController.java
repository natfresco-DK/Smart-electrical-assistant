package ek.dk.smartelectricalassistant.controllers;

import ek.dk.smartelectricalassistant.dto.Records;
import ek.dk.smartelectricalassistant.service.ElectricityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/electricity-prices")
public class UserController {

    private final ElectricityService electricityService;

    public UserController(ElectricityService electricityService) {
        this.electricityService = electricityService;
    }

    @GetMapping
    public List<Records.ElectricityPrice> getPrices(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "DK2") String zone) {
        LocalDate requestedDate = date != null ? date : LocalDate.now(ZoneId.of("Europe/Copenhagen"));
        return electricityService.getPrices(requestedDate, zone);
    }
}
