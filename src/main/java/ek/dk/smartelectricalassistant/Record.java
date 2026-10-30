package ek.dk.smartelectricalassistant;

import java.util.List;

public class Record{

    record ElectricityAdvice(
            String summary,
            List<String> reasons,
            String recommendation
    ) {}

}


