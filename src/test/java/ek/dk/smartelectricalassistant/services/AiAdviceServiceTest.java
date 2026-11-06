package ek.dk.smartelectricalassistant.services;

import ek.dk.smartelectricalassistant.dto.Records;
import ek.dk.smartelectricalassistant.model.MachineType;
import ek.dk.smartelectricalassistant.model.PriceArea;
import ek.dk.smartelectricalassistant.service.AiAdviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class AiAdviceServiceTest {
    private ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private AiAdviceService service;

    @BeforeEach
    void setUp() {
        chatClientBuilder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        service = new AiAdviceService(chatClientBuilder);
    }

    @Test
    void returnsEstimatedKwhAndSelectedWindowIdFromOneAiResponse() {
        Records.AiWindowSelection aiResponse = validResponse("candidate-1");
        when(responseSpec.entity(Records.AiWindowSelection.class))
                .thenReturn(aiResponse);

        Records.AiWindowSelection result =
                service.advise(request(), candidates());

        assertEquals(new BigDecimal("1.2"), result.estimatedKwh());
        assertEquals("candidate-1", result.selectedWindowId());
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void acceptsAnIdSuppliedByJavaAndIncludesCandidateDataInPrompt() {
        when(responseSpec.entity(Records.AiWindowSelection.class))
                .thenReturn(validResponse("candidate-2"));

        ArgumentCaptor<String> promptCaptor =
                ArgumentCaptor.forClass(String.class);

        service.advise(request(), candidates());

        verify(requestSpec).user(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("candidate-1"));
        assertTrue(prompt.contains("candidate-2"));
        assertTrue(prompt.contains("0.75"));
        assertTrue(prompt.contains("1.25"));
    }

    @Test
    void rejectsZeroAndNegativeEstimatedKwh() {
        for (BigDecimal estimatedKwh :
                List.of(BigDecimal.ZERO, new BigDecimal("-0.1"))) {
            when(responseSpec.entity(Records.AiWindowSelection.class))
                    .thenReturn(response(
                            estimatedKwh,
                            "candidate-1",
                            "summary",
                            List.of("reason"),
                            List.of("assumption")));

            assertThrows(IllegalArgumentException.class,
                    () -> service.advise(request(), candidates()));
        }
    }

    @Test
    void rejectsMissingExplanations() {
        when(responseSpec.entity(Records.AiWindowSelection.class))
                .thenReturn(response(
                        new BigDecimal("1.2"),
                        "candidate-1",
                        "",
                        List.of(),
                        List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> service.advise(request(), candidates()));
    }

    @Test
    void mapsProviderFailureToBadGateway() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("provider unavailable"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.advise(request(), candidates()));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    private Records.AiWindowSelection validResponse(String selectedWindowId) {
        return response(
                new BigDecimal("1.2"),
                selectedWindowId,
                "Candidate selected using the supplied prices.",
                List.of("The selected candidate has a lower average spot price."),
                List.of("Typical appliance consumption was assumed."));
    }

    private Records.AiWindowSelection response(
            BigDecimal estimatedKwh,
            String selectedWindowId,
            String summary,
            List<String> reasons,
            List<String> assumptions) {
        return new Records.AiWindowSelection(
                estimatedKwh,
                selectedWindowId,
                summary,
                reasons,
                assumptions);
    }

    private Records.AdviceRequest request() {
        return new Records.AdviceRequest(
                "Run it at the cheapest suitable time.",
                LocalDate.of(2026, 11, 6),
                PriceArea.DK2,
                Duration.ofHours(1),
                null,
                null,
                MachineType.DISHWASHER);
    }

    private List<Records.PriceWindow> candidates() {
        OffsetDateTime firstStart = OffsetDateTime.of(
                2026, 11, 6, 18, 0, 0, 0, ZoneOffset.ofHours(1));
        OffsetDateTime secondStart = firstStart.plusHours(1);

        return List.of(
                new Records.PriceWindow(
                        "candidate-1",
                        firstStart,
                        firstStart.plusHours(1),
                        new BigDecimal("1.25")),
                new Records.PriceWindow(
                        "candidate-2",
                        secondStart,
                        secondStart.plusHours(1),
                        new BigDecimal("0.75")));
    }
}
