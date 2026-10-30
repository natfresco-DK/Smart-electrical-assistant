package ek.dk.smartelectricalassistant;

import java.util.List;

record ElectricityAdvice(
        String summary,
        List<String> reasons,
        String recommendation
) {}


