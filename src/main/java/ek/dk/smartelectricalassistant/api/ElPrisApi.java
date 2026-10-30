package ek.dk.smartelectricalassistant.api;

import ek.dk.smartelectricalassistant.dto.Records;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Component
public class ElPrisApi {

    private final RestClient restClient;

    public ElPrisApi(
            @Value("${elpris.api.base-url:https://www.elprisenligenu.dk}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<Records.ElectricityPrice> getPrices(LocalDate date, String priceArea) {
        String normalizedArea = priceArea.toUpperCase(Locale.ROOT);
        if (!normalizedArea.equals("DK1") && !normalizedArea.equals("DK2")) {
            throw new ResponseStatusException(BAD_REQUEST, "priceArea must be DK1 or DK2");
        }

        try {
            Records.ElectricityPrice[] prices = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/prices/{year}/{monthDay}_{priceArea}.json")
                            .build(date.getYear(), date.toString().substring(5), normalizedArea))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ResponseStatusException(
                                BAD_GATEWAY, "Elpris API returned HTTP " + response.getStatusCode().value());
                    })
                    .body(Records.ElectricityPrice[].class);

            if (prices == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Elpris API returned an empty response");
            }
            return Arrays.asList(prices);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Could not fetch electricity prices", exception);
        }
    }
}
