package ch.thp.cas.chattenderfahrplan.journeyservice;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZURICH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZURICH);

    private final WebClient client;

    public JourneyService(WebClient journeyWebClient) {
        this.client = journeyWebClient;
    }

    /* =========================
       NEU: Sync-API für Tools
       ========================= */

    /** Textuelle Zusammenfassung: naechste passende Verbindung ab Zeitpunkt when. */
    public PlanResult planJourneyText(String originUIC, String destinationUIC, OffsetDateTime when) {
        JsonNode json = fetchTrips(originUIC, destinationUIC, when).block();
        // Nimmt die beste/erste Verbindung ab Zeitpunkt when und formatiert als Text
        return JourneyMapper.toPlanResultItinerary(json);
    }

    /** JSON-kompatible Liste: mehrere Verbindungen ab Zeitpunkt when (limit steuert Anzahl). */
    public List<FlatPlan> planJourneyJson(String originUIC, String destinationUIC, OffsetDateTime when, int limit) {
        JsonNode json = fetchTrips(originUIC, destinationUIC, when).block();
        // Bevorzugt direkte Flat-Listenabbildung (falls vorhanden)
        // TODO: Falls deine Mapper keine Liste liefern, ersatzweise Options->Flat umsetzen.
        List<FlatPlan> flats = JourneyMapper.toFlatPlans(json, Math.max(1, limit));
        return flats;
    }

    /** Rohantwort des Journey-Service als JSON-String (Debug, Trip-IDs, volle Felder). */
    public String rawTripSearch(String originUIC, String destinationUIC, OffsetDateTime when, int maxAlternatives) {
        TripsRequest req = toTripsRequest(originUIC, destinationUIC, when, false);
        // maxAlternatives wird aktuell clientseitig geschnitten (Mapper), Backend-Body bleibt minimal wie bisher.
        return client.post()
                .uri("/v3/trips/by-origin-destination")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Request-ID", UUID.randomUUID().toString())
                .bodyValue(req)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /* =========================
       Bestehende Reactive-API
       ========================= */

    /** Kompakte Uebersicht: mehrere Verbindungen (1 Option je Trip) – unverändert. */
    public Mono<PlanResult> planOptions(String originUIC, String destinationUIC, int maxOptions) {
        return fetchTrips(originUIC, destinationUIC)
                .map(json -> JourneyMapper.toPlanResultOptions(json, Math.max(1, maxOptions)));
    }

    /** Detailliert: genau 1 Verbindung als Itinerary mit allen Fahr-Legs – unverändert. */
    public Mono<PlanResult> planItinerary(String originUIC, String destinationUIC) {
        return fetchTrips(originUIC, destinationUIC)
                .map(JourneyMapper::toPlanResultItinerary);
    }

    /** Kompakt als Flat – unverändert. */
    public Mono<FlatPlan> planFlatOptions(String originUIC, String destinationUIC, int maxOptions) {
        return planOptions(originUIC, destinationUIC, maxOptions).map(FlatMapper::toFlat);
    }

    /** Detailliert als Flat (Basis für planJourneyText) – unverändert. */
    public Mono<FlatPlan> planFlatItinerary(String originUIC, String destinationUIC) {
        return planItinerary(originUIC, destinationUIC).map(FlatMapper::toFlat);
    }

    /* =========================
       Internals
       ========================= */

    /** Alt: ohne Zeitfilter (Kompatibilität). */
    private Mono<JsonNode> fetchTrips(String originUIC, String destinationUIC) {
        var req = new TripsRequest(originUIC, destinationUIC, null, null, false);
        return client.post()
                .uri("/v3/trips/by-origin-destination")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Request-ID", UUID.randomUUID().toString())
                .bodyValue(req)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /** Neu: mit Zeitfilter ab 'when' (Departure). */
    private Mono<JsonNode> fetchTrips(String originUIC, String destinationUIC, OffsetDateTime when) {
        var req = toTripsRequest(originUIC, destinationUIC, when, false);
        return client.post()
                .uri("/v3/trips/by-origin-destination")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Request-ID", UUID.randomUUID().toString())
                .bodyValue(req)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private TripsRequest toTripsRequest(String originUIC, String destinationUIC, OffsetDateTime when, boolean forArrival) {
        String d = when == null ? null : DATE.format(when);
        String t = when == null ? null : TIME.format(when);
        return new TripsRequest(originUIC, destinationUIC, d, t, forArrival);
    }

    /**
     * Minimales Body-Schema gemaess deiner bisherigen Implementierung.
     * origin, destination: UIC/ID
     * date: yyyy-MM-dd (Europe/Zurich)
     * time: HH:mm (Europe/Zurich)
     * forArrival: Abfahrts- (false) oder Ankunftssuche (true)
     */
    record TripsRequest(String origin, String destination, String date, String time, Boolean forArrival) {}
}
