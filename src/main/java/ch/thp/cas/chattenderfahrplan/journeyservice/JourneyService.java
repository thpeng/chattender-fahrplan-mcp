package ch.thp.cas.chattenderfahrplan.journeyservice;


import java.util.UUID;
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

    public Mono<String> plan(String origin, String destination) {
        var reqId = UUID.randomUUID().toString();

        return client.post()
                .uri("/v3/trips/by-origin-destination")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "de")
                .header("Request-ID", reqId)
                .bodyValue(TripsRequest.of(origin, destination))
                .retrieve()
                .onStatus(HttpStatusCode::isError, rsp ->
                        rsp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Journey error " + rsp.statusCode() + " " + msg))))
                .bodyToMono(String.class);
    }

    record TripsRequest(String origin, String destination, String date, String time, Boolean forArrival) {
        static TripsRequest of(String o, String d) { return new TripsRequest(o, d, null, null, false); }
    }
}

