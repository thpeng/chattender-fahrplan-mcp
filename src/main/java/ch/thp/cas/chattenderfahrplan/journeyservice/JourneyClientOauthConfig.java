package ch.thp.cas.chattenderfahrplan.journeyservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;


@Configuration
class JourneyClientOAuthConfig {

    @Bean
    ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository registrations,
            ReactiveOAuth2AuthorizedClientService clientService) {

        var provider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()   // wichtig
                .build();

        var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                registrations, clientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    WebClient journeyWebClient(
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${JOURNEY_SERVICE_BASE}") String baseUrl) {

        var oauth = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth.setDefaultClientRegistrationId("journey");

        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(oauth)                              // fügt automatisch Bearer-Token ein
                .defaultHeaders(h -> h.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
    }
}

