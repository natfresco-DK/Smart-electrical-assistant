package ek.dk.smartelectricalassistant.service;

import ek.dk.smartelectricalassistant.api.ElPrisApi;
import ek.dk.smartelectricalassistant.dto.Records;
import ek.dk.smartelectricalassistant.model.PriceArea;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ElectricityPriceService {

    private final ElPrisApi elPrisApi;

    public ElectricityPriceService(ElPrisApi elPrisApi) {
        this.elPrisApi = elPrisApi;
    }

    public List<Records.ElectricityPrice> getPrices(LocalDate date, PriceArea priceArea) {
        return elPrisApi.getPrices(date, priceArea);
    }
}
