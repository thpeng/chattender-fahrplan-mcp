package ch.thp.cas.chattenderfahrplan.journeyservice;

// 1) PlacesClient: Name -> UIC (StopPlace.id)

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class PlacesResolver {

    private final WebClient client;

    public PlacesResolver(WebClient journeyWebClient) {
        this.client = journeyWebClient;
    }

    public Mono<String> resolveStopPlaceId(String name, String lang) {
        return client.get()
                .uri(uri -> uri.path("/v3/places")
                        .queryParam("nameMatch", name)
                        .queryParam("type", "StopPlace")
                        .queryParam("limit", 1)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .header("Accept-Language", lang != null ? lang : "de")
                .retrieve()
                .bodyToMono(PlaceResponse.class)
                .flatMap(resp -> resp.firstStopPlaceId()
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("No StopPlace for: " + name))));
    }

    // Minimal DTOs für die Extraktion
    static final class PlaceResponse {
        public java.util.List<Place> places;
        Mono<String> firstStopPlaceId() {
            if (places == null) return Mono.empty();
            return places.stream()
                    .filter(p -> "StopPlace".equalsIgnoreCase(p.type) && p.id != null)
                    .findFirst().map(p -> Mono.just(String.valueOf(p.id)))
                    .orElse(Mono.empty());
        }
    }
    static final class Place { public String type; public String id; public String name; }
}

