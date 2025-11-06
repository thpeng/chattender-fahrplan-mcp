package ch.thp.cas.chattenderfahrplan;


import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Service
@Slf4j
public class TimetableService {

    public record Leg(String line, String dep, String arr) {}
    public record Journey(String from, String to, List<Leg> legs, String notice) {}

    @Tool(
            name = "planJourney",
            description = "Returns a mock journey plan between two Swiss stations. Inputs are plain station names."
    )
    public Journey planJourney(
            @ToolParam(description = "Origin station, e.g., 'Bern'") String from,
            @ToolParam(description = "Destination station, e.g., 'Zuerich HB'") String to
    ) {
        log.info("from: %s, to: %s", from, to);

        // MOCK: replace with real timetable lookup later
        var legs = List.of(
                new Leg("IC 1", "08:02 Bern", "08:56 Zuerich HB"),
                new Leg("S 6",  "09:04 Zuerich HB", "09:12 Zuerich Oerlikon")
        );
        return new Journey(from, to, legs, "Mock data for demo. Replace with real API later.");
    }
}
