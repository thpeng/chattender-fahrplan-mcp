package ch.thp.cas.chattenderfahrplan;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import ch.thp.cas.chattenderfahrplan.journeyservice.JourneyService;
import ch.thp.cas.chattenderfahrplan.journeyservice.PlacesResolver;
import ch.thp.cas.chattenderfahrplan.mapping.FlatPlan;
import ch.thp.cas.chattenderfahrplan.mapping.FlatTrip;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Mono;

/**
 * class to handle llm tool call. the prompts here influence the behavior of the llm.
 * effect of the prompts differ per llm.
 * intended as reactive, fell back to blocking behavior because the mcp clients couldn't handle reactive streams.
 */
@Service
@Slf4j
public class TimetableTool {

    private final JourneyService journeys;
    private final PlacesResolver placesResolver;

    public TimetableTool(JourneyService journeys, PlacesResolver placesResolver) {
        this.journeys = journeys;
        this.placesResolver = placesResolver;
    }

    // ---------------------------
    // TOOL 1: Kompaktes JSON (mehrere Optionen)
    // ---------------------------
    @Tool(
            name = "planJourneyJson",
            description =
                    """
                    Liefert eine kompakte Liste von Verbindungen als JSON zurueck. Erstelle eine Tabelle aus dem JSON mit Spalten:
                    Bezeichnung (service), Abfahrtszeit (dep), Abfahrtsgleis (fromQuay), Anbieter (operator),
                    Ankunftszeit (arr), Ankunftsgleis (toQuay) und Ziel des Zuges (dir).
                    - Eingabe: zwei Ortsnamen (z. B. "Zuerich HB", "Bern") oder UICs.
                    - Ausgabe: { "options": [ { "dep","arr","service","operator","fromQuay","toQuay","dir" } ] }
                    - "service" = fahrgastnahe Bezeichnung (z. B. "S 4"), nicht Zugnummer.
                    - Rueckgabe ist reines JSON (kein Text).

                    Beispiele:
                    {"from":"burgistein","to":"kaufdorf"}
                    {"from":"Zuerich HB","to":"Bern"}
                    """
    )
    public FlatPlan planJourneyJson(
            @ToolParam(
                    description =
                            """
                            Start-Ort (bevorzugt: Name, alternativ UIC).
                            Beispiele: "Zuerich HB", "Bern", "8503000".
                            """
            ) String from,
            @ToolParam(
                    description =
                            """
                            Ziel-Ort (bevorzugt: Name, alternativ UIC).
                            Beispiele: "Bern", "Kaufdorf", "8507000".
                            """
            ) String to) {

        Mono<String> o = isUIC(from) ? Mono.just(from) : placesResolver.resolveStopPlaceId(from, "de");
        Mono<String> d = isUIC(to)   ? Mono.just(to)   : placesResolver.resolveStopPlaceId(to, "de");

        // Mehrere kompakte Optionen
        return Mono.zip(o, d)
                .flatMap(t -> journeys.planFlatOptions(t.getT1(), t.getT2(), 5))
                .block();
    }

    // ---------------------------
    // TOOL 2: Prosa (eine Verbindung mit Umstiegen)
    // ---------------------------
    @Tool(
            name = "planJourneyText",
            description =
                    """
                    Eingabe: zwei Ortsnamen (z. B. "Zuerich HB", "Bern") oder UICs, die der Benutzer als from oder to bezeichnet.
                    Ausgabe: Gibt genau einen Satz zur naechsten passenden Verbindung zurueck.
                    Uebersetze die Rueckgabe des Tools in die Sprache, in der der Benutzer interagiert.
                    Regeln:
                    - Zeiten als HH:mm.
                    - Wenn kein Treffer: "Keine passende Verbindung gefunden."
                    """
    )
    public String planJourneyText(
            @ToolParam(
                    description =
                            """
                            Start-Ort (bevorzugt: Name, alternativ UIC).
                            Beispiele: "Burgistein", "Zuerich HB", "8503000".
                            """
            ) String from,
            @ToolParam(
                    description =
                            """
                            Ziel-Ort (bevorzugt: Name, alternativ UIC).
                            Beispiele: "Kaufdorf", "Bern", "8507000".
                            """
            ) String to) {

        Mono<String> o = isUIC(from) ? Mono.just(from) : placesResolver.resolveStopPlaceId(from, "de");
        Mono<String> d = isUIC(to)   ? Mono.just(to)   : placesResolver.resolveStopPlaceId(to, "de");

        return Mono.zip(o, d)
                // genau 1 Itinerary, alle Fahr-Legs fuer Umstiege
                .flatMap(t -> journeys.planFlatItinerary(t.getT1(), t.getT2()))
                .map(fp -> {
                    if (fp.options().isEmpty()) return "Keine passende Verbindung gefunden.";

                    var legs = fp.options();
                    FlatTrip first = legs.get(0);
                    FlatTrip last  = legs.get(legs.size() - 1);

                    String dep = hhmm(first.dep());
                    String arr = hhmm(last.arr());
                    String svc = nz(first.service(), "Zug");
                    String op  = nz(first.operator(), "");
                    String fq  = nz(first.fromQuay(), "-");
                    String tq  = nz(last.toQuay(), "-");
                    String dir = nz(first.dir(), "-");

                    String fromLabel = isUIC(from) ? nz(first.from(), "Abfahrt") : from;
                    String toLabel   = isUIC(to)   ? nz(last.to(),  "Ankunft")   : to;

                    String opPart = op.isBlank() ? "" : " von " + op;
                    String tqPart = tq.equals("-") ? "" : " an Gleis " + tq;

                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format(
                            "Der nächste Verbindung mit der %s%s faehrt in Richtung %s um %s ab %s auf Gleis %s. Die Reise erreicht das Ziel %s um %s%s.",
                            svc, opPart, dir, dep, fromLabel, fq, toLabel, arr, tqPart
                    ));

                    // Umstiege
                    for (int i = 1; i < legs.size(); i++) {
                        FlatTrip leg = legs.get(i);
                        String when = hhmm(leg.dep());
                        String svc2 = nz(leg.service(), "Zug");
                        String op2  = nz(leg.operator(), "");
                        String dir2 = nz(leg.dir(), "-");
                        String at   = nz(leg.from(), "-");
                        String op2Part = op2.isBlank() ? "" : " von " + op2;

                        sb.append(" Umstieg in ")
                                .append(at)
                                .append(" um ")
                                .append(when)
                                .append(": ")
                                .append(svc2)
                                .append(op2Part)
                                .append(" Richtung ")
                                .append(dir2)
                                .append('.');
                    }

                    return sb.toString();
                })
                .block();
    }

    // ---------------------------
    // Helper
    // ---------------------------
    private boolean isUIC(String s) { return s != null && s.matches("\\d{6,8}"); }

    private static String hhmm(String iso) {
        try { return DateTimeFormatter.ofPattern("HH:mm").format(OffsetDateTime.parse(iso)); }
        catch (Exception e) { return iso != null ? iso : "-"; }
    }
    private static String nz(String s, String dflt){ return (s == null || s.isBlank()) ? dflt : s; }
}
