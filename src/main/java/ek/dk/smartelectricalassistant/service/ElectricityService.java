package ek.dk.smartelectricalassistant.service;

import ek.dk.smartelectricalassistant.api.ElPrisApi;
import ek.dk.smartelectricalassistant.dto.Records;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ElectricityService {

    private final ElPrisApi elPrisApi;

    public ElectricityService(ElPrisApi elPrisApi) {
        this.elPrisApi = elPrisApi;
    }

    public List<Records.ElectricityPrice> getPrices(LocalDate date, String priceArea) {
        return elPrisApi.getPrices(date, priceArea);
    }
}
