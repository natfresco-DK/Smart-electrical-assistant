package ek.dk.smartelectricalassistant.service;

import ek.dk.smartelectricalassistant.dto.Records;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class AiAdviceService {
    public Records.AiWindowSelection advise(Records.AdviceRequest request, List<Records.PricedWindow> validCandidates) {

        return null;
    }

    private void validateInput(Records.AdviceRequest request, List<Records.PricedWindow> validCandidates) {
    if (request == null) {
        throw new IllegalArgumentException("request is required");
    }
    if (validCandidates == null || validCandidates.isEmpty()) {
        throw new IllegalArgumentException("validCandidates is required and cannot be empty");
    }
    Set<String> candidateIds = new java.util.HashSet<>();
    for (Records.PricedWindow candidate : validCandidates) {
        if (candidate == null) {
            throw new IllegalArgumentException("validCandidates cannot contain null elements");
        }
        if (candidate.candidateId() == null || candidate.candidateId().isBlank()) {
            throw new IllegalArgumentException("validCandidates cannot contain null or blank IDs");
        }
        if (!candidateIds.add(candidate.candidateId())) {
            throw new IllegalArgumentException("validCandidates cannot contain duplicate candidate IDs");
        }
        if (candidate.priceWindow() == null) {
            throw new IllegalArgumentException("validCandidates cannot contain a null price window");
        }
        Records.PriceWindow priceWindow = candidate.priceWindow();
        if (priceWindow.startTime() == null || priceWindow.endTime() == null) {
            throw new IllegalArgumentException("price windows must contain start and end times");
        }
        if (!priceWindow.endTime().isAfter(priceWindow.startTime())) {
            throw new IllegalArgumentException("price window end time must be after start time");
        }
        if (priceWindow.averageSpotPrice() == null) {
            throw new IllegalArgumentException("price windows must contain an average spot price");
        }
        if (candidate.estimatedCost() == null) {
            throw new IllegalArgumentException("validCandidates must contain an estimated cost");
        }
    }
}


private Records.AiWindowSelection requestAiAdvice(
        Records.AdviceRequest request,
        List<Records.PriceWindow> candidates) {
    // Build the prompt and make the single ChatClient call
    return null;
}

private Records.AiWindowSelection validateAiResponse(
        Records.AiWindowSelection response,
        List<Records.PriceWindow> candidates) {
    // Validate estimatedKwh, selectedWindowId and explanation fields
    return null;
}
}

