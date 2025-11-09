package ch.thp.cas.chattenderfahrplan.journeyservice;

import java.util.UUID;

import ch.thp.cas.chattenderfahrplan.mapping.FlatMapper;
import ch.thp.cas.chattenderfahrplan.mapping.FlatPlan;
import ch.thp.cas.chattenderfahrplan.mapping.PlanResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class JourneyService {

    private final WebClient client;

    public JourneyService(WebClient journeyWebClient) {
        this.client = journeyWebClient;
    }

    /* =========================
       Public API
       ========================= */

    /** Kompakte Übersicht: mehrere Verbindungen (1 Option je Trip) */
    public Mono<PlanResult> planOptions(String originUIC, String destinationUIC, int maxOptions) {
        return fetchTrips(originUIC, destinationUIC)
                .map(json -> JourneyMapper.toPlanResultOptions(json, Math.max(1, maxOptions)));
    }

    /** Detailliert: genau 1 Verbindung als Itinerary mit allen Fahr-Legs (für Umstiege) */
    public Mono<PlanResult> planItinerary(String originUIC, String destinationUIC) {
        return fetchTrips(originUIC, destinationUIC)
                .map(JourneyMapper::toPlanResultItinerary);
    }

    /** Kompakte Übersicht als Flat */
    public Mono<FlatPlan> planFlatOptions(String originUIC, String destinationUIC, int maxOptions) {
        return planOptions(originUIC, destinationUIC, maxOptions).map(FlatMapper::toFlat);
    }

    /** Detailliert als Flat (für planJourneyText) */
    public Mono<FlatPlan> planFlatItinerary(String originUIC, String destinationUIC) {
        return planItinerary(originUIC, destinationUIC).map(FlatMapper::toFlat);
    }


    /* =========================
       Internals
       ========================= */

    private Mono<JsonNode> fetchTrips(String originUIC, String destinationUIC) {
        var req = new TripsRequest(originUIC, destinationUIC, null, null, false);
        return client.post()
                .uri("/v3/trips/by-origin-destination")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "de")
                .header("Request-ID", UUID.randomUUID().toString())
                .bodyValue(req)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    record TripsRequest(String origin, String destination, String date, String time, Boolean forArrival) {}
}
