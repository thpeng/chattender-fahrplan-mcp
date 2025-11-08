package ch.thp.cas.chattenderfahrplan.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * usually authentication (or at least, a restriction who can use this mcp) is done with the x-api-key. Mistral / le chat
 * however enforces that the mcp has either no authentication (bad), oauth (fine in theory, but complex for a study project)
 * authorization header + basic auth (a hack) or authorization + bearer. so -> x-api-key and auth + bearer are supported.
 */
@Configuration
public class SecurityConfig {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            @Value("${MCP_API_KEY:}") String expectedApiKey) {

        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new IllegalStateException("MCP_API_KEY environment variable not set");
        }

        ServerAuthenticationConverter converter = exchange ->
                extractApiKey(exchange).map(key -> new UsernamePasswordAuthenticationToken(key, key));

        ReactiveAuthenticationManager authManager = authentication -> {
            String provided = (String) authentication.getCredentials();
            if (expectedApiKey.equals(provided)) {
                List<SimpleGrantedAuthority> auths = List.of(new SimpleGrantedAuthority("ROLE_MCP"));
                AbstractAuthenticationToken ok =
                        new UsernamePasswordAuthenticationToken("mcp-client", "n/a", auths);
                ok.setDetails("api-key");
                return Mono.just(ok);
            }
            return Mono.empty();
        };

        var apiKeyFilter = new AuthenticationWebFilter(authManager);
        apiKeyFilter.setServerAuthenticationConverter(converter);
        apiKeyFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.anyExchange());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(reg -> reg.anyExchange().authenticated())
                .build();
    }

    private Mono<String> extractApiKey(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders();

        String direct = headers.getFirst(API_KEY_HEADER);
        if (direct != null && !direct.isBlank()) {
            return Mono.just(direct.trim());
        }

        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            return Mono.empty();
        }

        if (auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = auth.substring(BEARER_PREFIX.length()).trim();
            if (!token.isBlank()) {
                return Mono.just(token);
            }
        }

        return Mono.empty();
    }
}
