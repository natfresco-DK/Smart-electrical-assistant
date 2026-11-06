package ek.dk.smartelectricalassistant.controllers;

import ek.dk.smartelectricalassistant.model.PriceArea;
import ek.dk.smartelectricalassistant.service.AdviceService;
import ek.dk.smartelectricalassistant.service.ElectricityPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private ElectricityPriceService electricityPriceService;
    private AdviceService adviceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        electricityPriceService = mock(ElectricityPriceService.class);
        adviceService = mock(AdviceService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(electricityPriceService, adviceService))
                .build();
    }

    @Test
    void mapsDateAndPriceAreaToService() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 2);
        when(electricityPriceService.getPrices(date, PriceArea.DK1)).thenReturn(List.of());

        mockMvc.perform(get("/api/electricity-prices")
                        .param("date", "2026-11-02")
                        .param("priceArea", "DK1"))
                .andExpect(status().isOk());

        verify(electricityPriceService).getPrices(date, PriceArea.DK1);
    }

    @Test
    void usesDk2AsDefaultPriceArea() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 2);
        when(electricityPriceService.getPrices(date, PriceArea.DK2)).thenReturn(List.of());

        mockMvc.perform(get("/api/electricity-prices")
                        .param("date", "2026-11-02"))
                .andExpect(status().isOk());

        verify(electricityPriceService).getPrices(date, PriceArea.DK2);
    }

    @Test
    void rejectsUnsupportedPriceArea() throws Exception {
        mockMvc.perform(get("/api/electricity-prices")
                        .param("date", "2026-11-02")
                        .param("priceArea", "DK3"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(electricityPriceService);
    }

    @Test
    void rejectsMissingDate() throws Exception {
        mockMvc.perform(get("/api/electricity-prices")
                        .param("priceArea", "DK2"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(electricityPriceService);
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        mockMvc.perform(get("/api/electricity-prices")
                        .param("date", "not-a-date")
                        .param("priceArea", "DK2"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(electricityPriceService);
    }
}
