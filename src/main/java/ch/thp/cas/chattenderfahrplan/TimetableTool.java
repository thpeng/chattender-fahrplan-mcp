package ch.thp.cas.chattenderfahrplan;


import java.util.List;

import ch.thp.cas.chattenderfahrplan.journeyservice.JourneyService;
import ch.thp.cas.chattenderfahrplan.journeyservice.PlacesResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class TimetableTool {


    private final JourneyService journeys;
    private final PlacesResolver placesResolver;

    public TimetableTool(JourneyService journeys, PlacesResolver placesResolver) {
        this.journeys = journeys;
        this.placesResolver = placesResolver;
    }

    @Tool(
            name = "planJourney",
            description = "Plan trip between two place names (e.g. 'Zurich HB', 'Bern')."
    )
    public String planJourney(
            @ToolParam(description = "Origin name, e.g. 'Zurich HB'") String from,
            @ToolParam(description = "Destination name, e.g. 'Bern'") String to) {
        log.info("got from: "+ from+" to: "+to);
        // Falls bereits UIC übergeben (reiner Digit-Check) → direkt verwenden
        Mono<String> origin = isUIC(from) ? Mono.just(from) : placesResolver.resolveStopPlaceId(from, "de");
        Mono<String> dest   = isUIC(to)   ? Mono.just(to)   : placesResolver.resolveStopPlaceId(to, "de");

        return Mono.zip(origin, dest)
                .flatMap(t -> journeys.plan(t.getT1(), t.getT2())).block();
    }

    private boolean isUIC(String s) { return s != null && s.matches("\\d{6,8}"); }
}