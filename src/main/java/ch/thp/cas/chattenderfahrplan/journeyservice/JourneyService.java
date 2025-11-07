package ch.thp.cas.chattenderfahrplan.journeyservice;


import java.util.UUID;

import ch.thp.cas.chattenderfahrplan.PlanResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
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

    public Mono<PlanResult> plan(String originUIC, String destinationUIC, int maxOptions) {
        var req = new TripsRequest(originUIC, destinationUIC, null, null, false);
        var reqId = UUID.randomUUID().toString();

        return client.post()
                .uri("/v3/trips/by-origin-destination")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "de")
                .header("Request-ID", reqId)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> JourneyMapper.toPlanResult(json, Math.max(1, maxOptions)));
    }

    record TripsRequest(String origin, String destination, String date, String time, Boolean forArrival) {}
}

