package ek.dk.smartelectricalassistant.service;

import ek.dk.smartelectricalassistant.dto.Records;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AiAdviceService {
    private static final String SYSTEM_PROMPT = """
            You are an electricity-usage scheduling assistant.
            
            Your task is to:
            1. Estimate the appliance's total electricity consumption in kWh.
            2. Select the most suitable usage window from the candidates supplied by Java.
            3. Explain your recommendation and state the assumptions used.
            
            Consumption estimation rules:
            - Use the supplied machine type, requested duration, user question, and any relevant preferences.
            - Estimate total consumption for the complete appliance cycle, not consumption per hour.
            - Make a reasonable assumption when the user's information is vague.
            - Do not ask follow-up questions.
            - State the consumption assumptions in the assumptions field.
            
            Window-selection rules:
            - Select exactly one candidate.
            - The selectedWindowId must exactly match one candidate ID supplied by Java.
            - Use the supplied average spot prices when comparing candidates.
            - Respect explicit user preferences such as earliest start, latest finish, or avoiding certain times.
            - If preferences conflict or are vague, prefer a lower supplied average spot price and explain that choice.
            - Do not invent candidate IDs, timestamps, prices, or monetary costs.
            - Do not calculate or mention monetary costs.
            
            Output rules:
            - Return only a response matching the AiWindowSelection structure.
            - estimatedKwh must be a positive number.
            - Include a concise summary.
            - Include reasons explaining both the preference handling and price comparison.
            - Include consumption-estimation and scheduling assumptions.
            - Do not include Markdown, extra fields, or explanatory text outside the structure.
            """;

    private String buildUserPrompt(
            Records.AdviceRequest request,
            List<Records.PriceWindow> candidates) {

        return """
                User question: %s
                Machine type: %s
                Duration: %s
                Date: %s
                Region: %s
                Earliest start: %s
                Latest end: %s
                
                Valid candidate windows:
                %s
                """.formatted(
                request.question(),
                request.machineType(),
                request.duration(),
                request.date(),
                request.region(),
                request.earliestStart(),
                request.latestEnd(),
                candidates
        );
    }

    private final ChatClient chatClient;

    public AiAdviceService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public Records.AiWindowSelection advise(Records.AdviceRequest request, List<Records.PriceWindow> validCandidates) {
        validateInput(request, validCandidates);
        Records.AiWindowSelection response = requestAiAdvice(request, validCandidates);
        return validateAiResponse(response, validCandidates);
    }

    private void validateInput(Records.AdviceRequest request, List<Records.PriceWindow> validCandidates) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (validCandidates == null || validCandidates.isEmpty()) {
            throw new IllegalArgumentException("validCandidates is required and cannot be empty");
        }
        Set<String> candidateIds = new HashSet<>();
        for (Records.PriceWindow candidate : validCandidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("validCandidates cannot contain null elements");
            }
            if (candidate.candidateId() == null || candidate.candidateId().isBlank()) {
                throw new IllegalArgumentException("validCandidates cannot contain null or blank IDs");
            }
            if (!candidateIds.add(candidate.candidateId())) {
                throw new IllegalArgumentException("validCandidates cannot contain duplicate candidate IDs");
            }
            if (candidate.startTime() == null || candidate.endTime() == null) {
                throw new IllegalArgumentException("price windows must contain start and end times");
            }
            if (!candidate.endTime().isAfter(candidate.startTime())) {
                throw new IllegalArgumentException("price window end time must be after start time");
            }
            if (candidate.averageSpotPrice() == null) {
                throw new IllegalArgumentException("price windows must contain an average spot price");
            }
        }
    }


    private Records.AiWindowSelection requestAiAdvice(Records.AdviceRequest request, List<Records.PriceWindow> candidates) {
        try {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request, candidates))
                    .call()
                    .entity(Records.AiWindowSelection.class);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ai advice provider failed or returned an invalid response",
                    e
            );
        }
    }

    private Records.AiWindowSelection validateAiResponse(Records.AiWindowSelection response, List<Records.PriceWindow> candidates) {
        if (response == null) {
            throw new IllegalArgumentException("AI response is required");
        }
        if (response.estimatedKwh() == null || response.estimatedKwh().signum() <= 0) {
            throw new IllegalArgumentException(
                    "AI response must contain a positive estimatedKwh");
        }
        if (response.selectedWindowId() == null || response.selectedWindowId().isBlank()) {
            throw new IllegalArgumentException("AI response must include a selected window ID");
        }
        if (response.summary() == null || response.summary().isBlank()) {
            throw new IllegalArgumentException("AI response must include a summary");
        }
        if (response.reasons() == null || response.reasons().isEmpty()) {
            throw new IllegalArgumentException("AI response must include reasons");
        }
        if (response.assumptions() == null || response.assumptions().isEmpty()) {
            throw new IllegalArgumentException("AI response must include assumptions");
        }
        for (String assumption : response.assumptions()) {
            if (assumption == null || assumption.isBlank()) {
                throw new IllegalArgumentException("AI response assumptions cannot contain null or blank values");
            }
        }
        for (String reason : response.reasons()) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("AI response reasons cannot contain null or blank values");
            }
        }
        return response;
    }
}
