package ch.thp.cas.chattenderfahrplan.journeyservice;

import ch.thp.cas.chattenderfahrplan.mapping.FlatMapper;
import ch.thp.cas.chattenderfahrplan.mapping.FlatPlan;
import ch.thp.cas.chattenderfahrplan.mapping.PlanResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class JourneyMapper {

    private JourneyMapper() {}

    // 1) Detailliert: genau 1 Verbindung als Itinerary (bestehende Logik)
    public static PlanResult toPlanResultItinerary(JsonNode root) {
        if (root == null) return PlanResult.of(List.of());
        for (JsonNode trip : root.path("trips")) {
            List<PlanResult.TripOption> legs = new ArrayList<>();
            JsonNode arr = trip.path("legs");
            if (!arr.isArray()) continue;
            for (JsonNode leg : arr) {
                if (!"PTRideLeg".equalsIgnoreCase(leg.path("type").asText())) continue;
                JsonNode sj = leg.path("serviceJourney");
                JsonNode stopPoints = sj.path("stopPoints");
                JsonNode prod = (sj.path("serviceProducts").isArray() && sj.path("serviceProducts").size()>0)
                        ? sj.path("serviceProducts").get(0) : null;
                StopPair pair = resolveDepArrStopPoints(stopPoints, prod);
                if (pair.depSp == null || pair.arrSp == null) continue;

                String dep = pickTime(pair.depSp.path("departure"));
                String arrT = pickTime(pair.arrSp.path("arrival"));
                String fq = pickQuay(pair.depSp.path("departure"));
                String tq = pickQuay(pair.arrSp.path("arrival"));
                String fromName = textOrNull(pair.depSp.path("place").path("name"));
                String toName   = textOrNull(pair.arrSp.path("place").path("name"));

                String service = null;
                if (prod != null) {
                    service = textOrNull(prod.path("nameFormatted"));
                    if (service == null) {
                        String name = textOrNull(prod.path("name"));
                        if (name != null) {
                            var t = name.split("\\s+");
                            service = (t.length>=2) ? (t[0] + " " + t[1]).trim() : name.trim();
                        }
                    }
                    if (service == null) {
                        String sub = textOrNull(prod.path("vehicleMode").path("vehicleSubModeShortName"));
                        String line = textOrNull(prod.path("line"));
                        if (sub != null && line != null) service = (sub + " " + line).trim();
                    }
                }
                String operator = prod != null ? textOrNull(prod.path("operator").path("name")) : null;

                String direction = null;
                if (sj.path("directions").isArray() && sj.path("directions").size()>0)
                    direction = textOrNull(sj.path("directions").get(0).path("name"));
                else if (leg.path("directions").isArray() && leg.path("directions").size()>0)
                    direction = textOrNull(leg.path("directions").get(0).path("name"));

                legs.add(new PlanResult.TripOption(
                        dep, arrT, service, operator, fq, tq, direction, fromName, toName
                ));
            }
            if (!legs.isEmpty()) return PlanResult.of(legs); // nur erste Verbindung
        }
        return PlanResult.of(List.of());
    }

    // 2) Optionen: mehrere Verbindungen kompakt (bestehende Logik)
    public static PlanResult toPlanResultOptions(JsonNode root, int maxOptions) {
        if (root == null) return PlanResult.of(List.of());
        List<PlanResult.TripOption> options = new ArrayList<>();

        for (JsonNode trip : root.path("trips")) {
            JsonNode legs = trip.path("legs");
            if (!legs.isArray() || legs.size()==0) continue;

            JsonNode firstRide = null, lastRide = null;
            for (JsonNode leg : legs) {
                if ("PTRideLeg".equalsIgnoreCase(leg.path("type").asText())) {
                    if (firstRide == null) firstRide = leg;
                    lastRide = leg;
                }
            }
            if (firstRide == null || lastRide == null) continue;

            JsonNode sjFirst = firstRide.path("serviceJourney");
            JsonNode sjLast  = lastRide.path("serviceJourney");
            JsonNode prodFirst = (sjFirst.path("serviceProducts").isArray() && sjFirst.path("serviceProducts").size()>0)
                    ? sjFirst.path("serviceProducts").get(0) : null;

            StopPair depPair = resolveDepArrStopPoints(sjFirst.path("stopPoints"), prodFirst);
            StopPair arrPair = resolveDepArrStopPoints(sjLast.path("stopPoints"),
                    (sjLast.path("serviceProducts").isArray() && sjLast.path("serviceProducts").size()>0)
                            ? sjLast.path("serviceProducts").get(0) : null);

            if (depPair.depSp == null || arrPair.arrSp == null) continue;

            String dep = pickTime(depPair.depSp.path("departure"));
            String arrT = pickTime(arrPair.arrSp.path("arrival"));
            String fq = pickQuay(depPair.depSp.path("departure"));
            String tq = pickQuay(arrPair.arrSp.path("arrival"));

            String fromName = textOrNull(depPair.depSp.path("place").path("name"));
            String toName   = textOrNull(arrPair.arrSp.path("place").path("name"));

            String service = null;
            if (prodFirst != null) {
                service = textOrNull(prodFirst.path("nameFormatted"));
                if (service == null) {
                    String name = textOrNull(prodFirst.path("name"));
                    if (name != null) {
                        var t = name.split("\\s+");
                        service = (t.length>=2) ? (t[0] + " " + t[1]).trim() : name.trim();
                    }
                }
                if (service == null) {
                    String sub = textOrNull(prodFirst.path("vehicleMode").path("vehicleSubModeShortName"));
                    String line = textOrNull(prodFirst.path("line"));
                    if (sub != null && line != null) service = (sub + " " + line).trim();
                }
            }
            String operator = prodFirst != null ? textOrNull(prodFirst.path("operator").path("name")) : null;

            String direction = null;
            if (sjFirst.path("directions").isArray() && sjFirst.path("directions").size()>0)
                direction = textOrNull(sjFirst.path("directions").get(0).path("name"));
            else if (firstRide.path("directions").isArray() && firstRide.path("directions").size()>0)
                direction = textOrNull(firstRide.path("directions").get(0).path("name"));

            options.add(new PlanResult.TripOption(
                    dep, arrT, service, operator, fq, tq, direction, fromName, toName
            ));

            if (maxOptions > 0 && options.size() >= maxOptions) break;
        }
        return PlanResult.of(options);
    }

    // 3) Neu: Liste flacher Pläne (für listJourneys/listAndPlanJourneys)
    public static List<FlatPlan> toFlatPlans(JsonNode root, int maxOptions) {
        PlanResult pr = toPlanResultOptions(root, Math.max(1, maxOptions));
        List<FlatPlan> out = new ArrayList<>();
        for (PlanResult.TripOption opt : pr.options()) {
            // jeweils ein Einzel-PlanResult aus der Option bauen und dann flatten
            FlatPlan fp = FlatMapper.toFlat(PlanResult.of(List.of(opt)));
            out.add(fp);
        }
        return out;
    }

    // ---------- Helpers (unverändert) ----------

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

    private static StopPair resolveDepArrStopPoints(JsonNode stopPoints, JsonNode product) {
        if (stopPoints == null || !stopPoints.isArray() || stopPoints.size() == 0) {
            return new StopPair(null, null);
        }

        Integer idxFrom = product != null ? asIntegerOrNull(product.path("routeIndexFrom")) : null;
        Integer idxTo   = product != null ? asIntegerOrNull(product.path("routeIndexTo"))   : null;

        JsonNode depSp = (idxFrom != null) ? findByRouteIndex(stopPoints, idxFrom) : null;
        JsonNode arrSp = (idxTo   != null) ? findByRouteIndex(stopPoints, idxTo)   : null;

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

        if (depSp == null) depSp = findByUseOrFlag(stopPoints, true);
        if (arrSp == null) arrSp = findByUseOrFlag(stopPoints, false);

        if (depSp == null) depSp = findFirstWith(stopPoints, "departure");
        if (arrSp == null) arrSp = findLastWith(stopPoints, "arrival");

        return new StopPair(depSp, arrSp);
    }

    private static Integer asIntegerOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try { return n.asInt(); } catch (Exception e) { return null; }
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
        if (wantDeparture) {
            for (JsonNode sp : stopPoints) {
                if ("ACCESS".equalsIgnoreCase(sp.path("stopUse").asText(""))) return sp;
            }
        } else {
            for (int i = stopPoints.size() - 1; i >= 0; i--) {
                JsonNode sp = stopPoints.get(i);
                if ("EGRESS".equalsIgnoreCase(sp.path("stopUse").asText(""))) return sp;
            }
        }
        if (wantDeparture) {
            for (JsonNode sp : stopPoints) {
                if (sp.path("forBoarding").asBoolean(false)) return sp;
            }
        } else {
            for (int i = stopPoints.size() - 1; i >= 0; i--) {
                JsonNode sp = stopPoints.get(i);
                if (sp.path("forAlighting").asBoolean(false)) return sp;
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

    private static String pickTime(JsonNode when) {
        if (when == null || when.isMissingNode()) return null;
        String rt = textOrNull(when.path("timeRt"));
        if (rt != null) return rt;
        return textOrNull(when.path("timeAimed"));
    }

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
