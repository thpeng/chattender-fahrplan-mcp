package ch.thp.cas.chattenderfahrplan.infrastructure;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

import java.util.List;


@Configuration
public class SecurityConfig {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            @Value("${MCP_API_KEY:}") String expectedApiKey) {

        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new IllegalStateException("MCP_API_KEY environment variable not set");
        }

        // Converter: liest API Key aus Header und baut ein Auth-Token
        ServerAuthenticationConverter converter = exchange -> Mono.justOrEmpty(
                        exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER))
                .map(key -> new UsernamePasswordAuthenticationToken(key, key));

        // Reactive AuthenticationManager: vergleicht gegen erwarteten Key
        var authManager = (org.springframework.security.authentication.ReactiveAuthenticationManager) authentication -> {
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
}

