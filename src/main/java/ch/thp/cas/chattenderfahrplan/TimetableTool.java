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
    // TOOL 1: Kompaktes JSON
    // ---------------------------
    @Tool(
            name = "planJourneyJson",
            description =
                    """
                    Liefert eine kompakte Liste von Verbindungen als JSON zurück. Erstelle eine Tabelle aus dem json mit spalten 
                    Bezeichnung (service) Abfahrtszeit (dep), Abfahrtsgleis (fromQuay), Anbieter (operator), Ankunftszeit (arr), Ankunftsgleis (toquay) und Ziel des Zuges (dir). .
                    - Eingabe: zwei Ortsnamen (z.B. "Zürich HB", "Bern") oder UICs. 
                    - Ausgabe: { "options": [ { "dep","arr","service","operator","fromQuay","toQuay","dir" } ] }
                    - "service" = fahrgastnahe Bezeichnung (z.B. "S 4"), nicht Zugnummer.
                    - Rückgabe ist **reines JSON** (kein Text).
                    
                    Beispiele  wie du die Eingabe mappen sollst:
                    {"from":"burgistein","to":"kaufdorf"}
                    {"from":"Zürich HB","to":"Bern"}
                    """
    )
    public FlatPlan planJourneyJson(
            @ToolParam(
                    description =
                            """
                            Start-Ort (Bevorzugt: Name, alternativ UIC).
                            Beispiele: "Zürich HB", "Bern", "8503000".
                            """
            ) String from,
            @ToolParam(
                    description =
                            """
                            Ziel-Ort (Bevorzugt: Name, alternativ UIC).
                            Beispiele: "Bern", "Kaufdorf", "8507000".
                            """
            ) String to) {

        Mono<String> o = isUIC(from) ? Mono.just(from) : placesResolver.resolveStopPlaceId(from, "de");
        Mono<String> d = isUIC(to)   ? Mono.just(to)   : placesResolver.resolveStopPlaceId(to, "de");
        return Mono.zip(o, d).flatMap(t -> journeys.planFlat(t.getT1(), t.getT2(), 5)).block();
    }

    // ---------------------------
    // TOOL 2: Prosa (eine Zeile)
    // ---------------------------
    @Tool(
            name = "planJourneyText",
            description =
                    """
                    Eingabe: zwei Ortsnamen (z.B. "Zürich HB", "Bern") oder UICs die der benutzer als from oder to bezeichnet. 
                    Ausgabe: Gibt genau **einen** Satz zur nächsten passenden Verbindung zurück (keine Liste, keine Zusatztexte).
                    Übersetze die Rückgabe des Tools unbedingt in die Sprache wie der Benutzer mit dir interagiert!
                    Regeln:
                    - Zeiten als HH:mm.
                    - Wenn kein Treffer: "Keine passende Verbindung gefunden."
                    """
    )
    public String planJourneyText(
            @ToolParam(
                    description =
                            """
                            Start-Ort (Bevorzugt: Name, alternativ UIC).
                            Beispiele: "Burgistein", "Zürich HB", "8503000".
                            """
            ) String from,
            @ToolParam(
                    description =
                            """
                            Ziel-Ort (Bevorzugt: Name, alternativ UIC).
                            Beispiele: "Kaufdorf", "Bern", "8507000".
                            """
            ) String to) {

        Mono<String> o = isUIC(from) ? Mono.just(from) : placesResolver.resolveStopPlaceId(from, "de");
        Mono<String> d = isUIC(to)   ? Mono.just(to)   : placesResolver.resolveStopPlaceId(to, "de");

        return Mono.zip(o, d)
                .flatMap(t -> journeys.planFlat(t.getT1(), t.getT2(), 1))
                .map(fp -> {
                    if (fp.options().isEmpty()) return "Keine passende Verbindung gefunden.";
                    FlatTrip x = fp.options().get(0);

                    String dep = hhmm(x.dep());
                    String arr = hhmm(x.arr());
                    String svc = nz(x.service(), "Zug");
                    String op  = nz(x.operator(), "");
                    String fq  = nz(x.fromQuay(), "-");
                    String tq  = nz(x.toQuay(), "-");
                    String dir = nz(x.dir(), "-");

                    String fromLabel = isUIC(from) ? "Abfahrt" : from;
                    String toLabel   = isUIC(to)   ? "Ankunft" : to;

                    String opPart = op.isBlank() ? "" : " von " + op;
                    String tqPart = tq.equals("-") ? "" : " an Gleis " + tq;

                    return String.format(
                            "Der nächste %s%s fährt in Richtung %s um %s ab %s auf Gleis %s und erreicht %s um %s%s.",
                            svc, opPart, dir, dep, fromLabel, fq, toLabel, arr, tqPart
                    );
                }).block();
    }

    // ---------------------------
    // Helper
    // ---------------------------
    private boolean isUIC(String s) { return s != null && s.matches("\\d{6,8}"); }

    private static String hhmm(String iso) {
        try { return DateTimeFormatter.ofPattern("HH:mm").format(OffsetDateTime.parse(iso)); }
        catch (Exception e) { return iso != null ? iso : "-"; }
    }
    private static String nz(String s, String dflt){ return (s==null || s.isBlank()) ? dflt : s; }
}