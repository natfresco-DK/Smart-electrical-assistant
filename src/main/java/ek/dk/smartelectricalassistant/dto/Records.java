package ek.dk.smartelectricalassistant.dto;

import java.util.List;

public class Records{

    record ElectricityAdvice(
            String summary,
            List<String> reasons,
            String recommendation
    ) {}

    record geminiRequest(String prompt){}

}


