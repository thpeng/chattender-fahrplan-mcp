package ch.thp.cas.chattenderfahrplan.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;


/**
 * claude integrates only with mcp server if they either are unauthenticated (duh!) or use oauth2.
 * Since oauth2 for a poc is out of question the solution (hacky) is to offer a pathsegment where claude can send
 * the key as part of the url(path). this is unsecure because it can be logged. For example after ssl termination in
 * gcp and before the request lands.
 *
 * DO NOT USE THIS IN PRODUCTION!
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class PathKeyRewriteFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    // Optional: nur v4 oder v7 erlauben (statt UUID.fromString)
    private static final Pattern UUID_V4_OR_V7 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[47][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().pathWithinApplication().value();
        var seg = org.springframework.util.StringUtils.tokenizeToStringArray(path, "/");
        log.info(path);
        if (seg.length >= 2 && isUuid(seg[0])) {
            var key = seg[0];
            var newPath = "/" + String.join("/", Arrays.copyOfRange(seg, 1, seg.length));
            ServerHttpRequest mutatedReq = exchange.getRequest()
                    .mutate()
                    .path(newPath)
                    .headers(h -> { if (!h.containsKey(API_KEY_HEADER)) h.add(API_KEY_HEADER, key); })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedReq).build());
        }
        return chain.filter(exchange);
    }

    private boolean isUuid(String s) {
        // Variante A: tolerant (alle RFC-4122 Versionen)
        try { UUID.fromString(s); return true; } catch (IllegalArgumentException e) { return false; }
        // Variante B (stattdessen nutzen): return UUID_V4_OR_V7.matcher(s).matches();
    }
}
