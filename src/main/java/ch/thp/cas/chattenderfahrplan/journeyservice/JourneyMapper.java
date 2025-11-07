package ch.thp.cas.chattenderfahrplan.journeyservice;


import ch.thp.cas.chattenderfahrplan.mapping.PlanResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class JourneyMapper {

    private JourneyMapper() {}

    public static PlanResult toPlanResult(JsonNode root, int limit) {
        List<PlanResult.TripOption> out = new ArrayList<>();
        if (root == null) return PlanResult.of(out);

        for (JsonNode trip : root.path("trips")) {
            // nimm die erste Ride-Leg (Zug)
            JsonNode leg = trip.path("legs")
                    .findValue("type") != null ? findFirstRideLeg(trip.path("legs")) : null;
            if (leg == null) continue;

            // dep/arr StopPoints (mit departure/arrival Feldern)
            JsonNode depSp = findFirstWith(leg.path("serviceJourney").path("stopPoints"), "departure");
            JsonNode arrSp = findFirstWith(leg.path("serviceJourney").path("stopPoints"), "arrival");

            String depTime = depSp.path("departure").path("timeAimed").asText(null);
            String arrTime = arrSp.path("arrival").path("timeAimed").asText(null);

            String fromQuay = firstNonBlank(
                    depSp.path("departure").path("quayFormatted").asText(null),
                    depSp.path("departure").path("quayRt").path("name").asText(null),
                    depSp.path("departure").path("quayAimed").path("name").asText(null)
            );
            String toQuay = firstNonBlank(
                    arrSp.path("arrival").path("quayFormatted").asText(null),
                    arrSp.path("arrival").path("quayRt").path("name").asText(null),
                    arrSp.path("arrival").path("quayAimed").path("name").asText(null)
            );

            JsonNode product = leg.path("serviceProducts").isArray() && leg.path("serviceProducts").size() > 0
                    ? leg.path("serviceProducts").get(0) : null;
// neu:
            String serviceLabel = null;
            if (product != null) {
                // 1) bevorzugt: "S 4"
                serviceLabel = textOrNull(product.path("nameFormatted"));
                // 2) alternativ: "S 4 15427" -> kürzen auf "S 4"
                if (serviceLabel == null) {
                    String name = textOrNull(product.path("name"));         // z.B. "S 4 15427"
                    if (name != null) {
                        int sp = name.indexOf(' ');
                        serviceLabel = sp > 0 ? name.substring(0, sp + 2).trim() : name;
                    }
                }
                // 3) fallback: Submode + Line, z.B. "S 4"
                if (serviceLabel == null) {
                    String sub = textOrNull(product.path("vehicleMode").path("vehicleSubModeShortName")); // "S"
                    String line = textOrNull(product.path("line")); // "4"
                    if (sub != null && line != null) serviceLabel = (sub + " " + line).trim();
                }
            }

            String operator = product != null ? product.path("operator").path("name").asText(null) : null;

            String direction = null;
            if (leg.path("serviceJourney").path("directions").isArray() && leg.path("serviceJourney").path("directions").size() > 0) {
                direction = leg.path("serviceJourney").path("directions").get(0).path("name").asText(null);
            } else if (leg.path("directions").isArray() && leg.path("directions").size() > 0) {
                direction = leg.path("directions").get(0).path("name").asText(null);
            }

            out.add(new PlanResult.TripOption(depTime, arrTime, serviceLabel, operator, fromQuay, toQuay, direction));
            if (out.size() >= limit) break;
        }
        return PlanResult.of(out);
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText(null);
        return (s != null && !s.isBlank()) ? s : null;
    }

    private static JsonNode findFirstRideLeg(JsonNode legs) {
        if (legs == null || !legs.isArray()) return null;
        for (JsonNode leg : legs) {
            if ("PTRideLeg".equalsIgnoreCase(leg.path("type").asText()) &&
                    "TRAIN".equalsIgnoreCase(leg.path("mode").asText())) {
                return leg;
            }
        }
        return null;
    }

    private static JsonNode findFirstWith(JsonNode stopPoints, String fieldName) {
        if (stopPoints == null || !stopPoints.isArray()) return null;
        for (JsonNode sp : stopPoints) {
            if (sp.has(fieldName)) return sp;
        }
        // fallback: erstes/letztes Element
        return "departure".equals(fieldName) ? (stopPoints.size() > 0 ? stopPoints.get(0) : null)
                : (stopPoints.size() > 0 ? stopPoints.get(stopPoints.size()-1) : null);
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
}

