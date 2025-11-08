package ch.thp.cas.chattenderfahrplan.journeyservice;

import ch.thp.cas.chattenderfahrplan.mapping.PlanResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class JourneyMapper {

    private JourneyMapper() {}

    /**
     * Mappt die Journey-API-Response auf ein flaches, LLM-freundliches Ergebnis.
     * Wichtiger Fix: StopPoints für Abfahrt/Ankunft via routeIndex matchen
     * und NICHT als Arrayindex interpretieren.
     */
    public static PlanResult toPlanResult(JsonNode root, int limit) {
        List<PlanResult.TripOption> out = new ArrayList<>();
        if (root == null) return PlanResult.of(out);

        for (JsonNode trip : root.path("trips")) {
            JsonNode leg = findFirstRideLeg(trip.path("legs"));
            if (leg == null) continue;

            JsonNode sj = leg.path("serviceJourney");
            JsonNode stopPoints = sj.path("stopPoints");

            // Produkt immer aus serviceJourney lesen
            JsonNode product = (sj.path("serviceProducts").isArray() && sj.path("serviceProducts").size() > 0)
                    ? sj.path("serviceProducts").get(0) : null;

            // Dep/Arr StopPoint korrekt bestimmen
            StopPair pair = resolveDepArrStopPoints(stopPoints, product);
            if (pair.depSp == null || pair.arrSp == null) continue;

            // Zeiten: Echtzeit vor Aimed
            String depTime = pickTime(pair.depSp.path("departure"));
            String arrTime = pickTime(pair.arrSp.path("arrival"));

            // Gleise: Rt → Formatted → Aimed
            String fromQuay = pickQuay(pair.depSp.path("departure"));
            String toQuay   = pickQuay(pair.arrSp.path("arrival"));

            // Service-Label
            String serviceLabel = null;
            if (product != null) {
                serviceLabel = textOrNull(product.path("nameFormatted"));
                if (serviceLabel == null) {
                    String name = textOrNull(product.path("name"));
                    if (name != null) {
                        String[] tokens = name.split("\\s+");
                        serviceLabel = (tokens.length >= 2) ? (tokens[0] + " " + tokens[1]).trim() : name.trim();
                    }
                }
                if (serviceLabel == null) {
                    String sub = textOrNull(product.path("vehicleMode").path("vehicleSubModeShortName"));
                    String line = textOrNull(product.path("line"));
                    if (sub != null && line != null) serviceLabel = (sub + " " + line).trim();
                }
            }

            // Operator
            String operator = product != null ? textOrNull(product.path("operator").path("name")) : null;

            // Richtung
            String direction = null;
            if (sj.path("directions").isArray() && sj.path("directions").size() > 0) {
                direction = textOrNull(sj.path("directions").get(0).path("name"));
            } else if (leg.path("directions").isArray() && leg.path("directions").size() > 0) {
                direction = textOrNull(leg.path("directions").get(0).path("name"));
            }

            out.add(new PlanResult.TripOption(
                    depTime,
                    arrTime,
                    serviceLabel,
                    operator,
                    fromQuay,
                    toQuay,
                    direction
            ));

            if (out.size() >= limit) break;
        }
        return PlanResult.of(out);
    }

    // ---------- Helpers ----------

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText(null);
        return (s != null && !s.isBlank()) ? s : null;
    }

    private static JsonNode findFirstRideLeg(JsonNode legs) {
        if (legs == null || !legs.isArray()) return null;
        for (JsonNode leg : legs) {
            if ("PTRideLeg".equalsIgnoreCase(leg.path("type").asText())
                    && "TRAIN".equalsIgnoreCase(leg.path("mode").asText())) {
                return leg;
            }
        }
        return null;
    }

    /**
     * Dep/Arr via routeIndex matchen. Fallbacks:
     * - wenn nicht gefunden, Arrayindex aus Offset berechnen
     * - sonst Abfahrt: erstes mit departure, Ankunft: letztes mit arrival
     * - bei Flags: für Arrival vom Ende her suchen
     */
    private static StopPair resolveDepArrStopPoints(JsonNode stopPoints, JsonNode product) {
        if (stopPoints == null || !stopPoints.isArray() || stopPoints.size() == 0) {
            return new StopPair(null, null);
        }

        Integer idxFrom = product != null ? asIntegerOrNull(product.path("routeIndexFrom")) : null;
        Integer idxTo   = product != null ? asIntegerOrNull(product.path("routeIndexTo"))   : null;

        JsonNode depSp = (idxFrom != null) ? findByRouteIndex(stopPoints, idxFrom) : null;
        JsonNode arrSp = (idxTo   != null) ? findByRouteIndex(stopPoints, idxTo)   : null;

        // Fallback: Offset aus erstem routeIndex ableiten
        if ((depSp == null || arrSp == null) && stopPoints.size() > 0) {
            Integer firstRi = asIntegerOrNull(stopPoints.get(0).path("routeIndex"));
            if (firstRi != null) {
                if (depSp == null && idxFrom != null) {
                    int pos = idxFrom - firstRi;
                    if (pos >= 0 && pos < stopPoints.size()) depSp = stopPoints.get(pos);
                }
                if (arrSp == null && idxTo != null) {
                    int pos = idxTo - firstRi;
                    if (pos >= 0 && pos < stopPoints.size()) arrSp = stopPoints.get(pos);
                }
            }
        }

        // weitere Fallbacks über Use/Flags
        if (depSp == null) depSp = findByUseOrFlag(stopPoints, true);
        if (arrSp == null) arrSp = findByUseOrFlag(stopPoints, false);

        // letzte Fallbackstufe über vorhandene Felder
        if (depSp == null) depSp = findFirstWith(stopPoints, "departure");
        if (arrSp == null) arrSp = findLastWith(stopPoints, "arrival");

        return new StopPair(depSp, arrSp);
    }

    private static Integer asIntegerOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try {
            return n.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode findByRouteIndex(JsonNode stopPoints, int target) {
        if (stopPoints == null || !stopPoints.isArray()) return null;
        for (JsonNode sp : stopPoints) {
            if (sp.has("routeIndex") && sp.path("routeIndex").asInt() == target) {
                return sp;
            }
        }
        return null;
    }

    private static JsonNode findByUseOrFlag(JsonNode stopPoints, boolean wantDeparture) {
        // Zuerst stopUse
        if (wantDeparture) {
            for (JsonNode sp : stopPoints) {
                if ("ACCESS".equalsIgnoreCase(sp.path("stopUse").asText(""))) return sp;
            }
        } else {
            // Arrival: vom Ende her EGRESS oder forAlighting bevorzugen
            for (int i = stopPoints.size() - 1; i >= 0; i--) {
                JsonNode sp = stopPoints.get(i);
                if ("EGRESS".equalsIgnoreCase(sp.path("stopUse").asText(""))) return sp;
            }
        }
        // Dann Flags
        if (wantDeparture) {
            for (JsonNode sp : stopPoints) {
                if (sp.path("forBoarding").asBoolean(false)) return sp;
            }
        } else {
            for (int i = stopPoints.size() - 1; i >= 0; i--) {
                JsonNode sp = stopPoints.get(i);
                if (sp.path("forAlighting").asBoolean(false)) return sp;
                // falls nicht gesetzt, aber arrival vorhanden ist, ebenfalls ok
                if (sp.has("arrival")) return sp;
            }
        }
        return null;
    }

    private static JsonNode findFirstWith(JsonNode stopPoints, String fieldName) {
        if (stopPoints == null || !stopPoints.isArray()) return null;
        for (JsonNode sp : stopPoints) {
            if (sp.has(fieldName)) return sp;
        }
        return stopPoints.size() > 0 ? stopPoints.get(0) : null;
    }

    private static JsonNode findLastWith(JsonNode stopPoints, String fieldName) {
        if (stopPoints == null || !stopPoints.isArray() || stopPoints.size() == 0) return null;
        for (int i = stopPoints.size() - 1; i >= 0; i--) {
            JsonNode sp = stopPoints.get(i);
            if (sp.has(fieldName)) return sp;
        }
        return stopPoints.get(stopPoints.size() - 1);
    }

    // bevorzugt Echtzeit
    private static String pickTime(JsonNode when) {
        if (when == null || when.isMissingNode()) return null;
        String rt = textOrNull(when.path("timeRt"));
        if (rt != null) return rt;
        return textOrNull(when.path("timeAimed"));
    }

    // bevorzugt Echtzeit-Gleis; nur sinnvolle Werte
    private static String pickQuay(JsonNode when) {
        if (when == null || when.isMissingNode() || when.isNull()) return null;
        String rt = textOrNull(when.path("quayRt").path("name"));
        if (isValidQuay(rt)) return rt;
        String fmt = textOrNull(when.path("quayFormatted"));
        if (isValidQuay(fmt)) return fmt;
        String aimed = textOrNull(when.path("quayAimed").path("name"));
        if (isValidQuay(aimed)) return aimed;
        return null;
    }

    private static boolean isValidQuay(String s) {
        if (s == null) return false;
        String t = s.trim();
        return !(t.isEmpty() || "-".equals(t) || "?".equals(t));
    }

    private static final class StopPair {
        final JsonNode depSp;
        final JsonNode arrSp;
        StopPair(JsonNode depSp, JsonNode arrSp) { this.depSp = depSp; this.arrSp = arrSp; }
    }
}
