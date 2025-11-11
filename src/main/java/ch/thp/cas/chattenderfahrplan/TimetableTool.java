package ch.thp.cas.chattenderfahrplan;

import ch.thp.cas.chattenderfahrplan.journeyservice.JourneyService;
import ch.thp.cas.chattenderfahrplan.journeyservice.PlacesResolver;
import ch.thp.cas.chattenderfahrplan.mapping.FlatPlan;
import ch.thp.cas.chattenderfahrplan.mapping.PlanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Toolset fuer LLM-basierte Fahrplanauskuenfte (Bring-your-own-LLM).
 * <p>
 * Nutzungshinweise fuer LLM:
 * - nextJourney → verwenden bei "jetzt", "naechste Abfahrt", "bald"
 * - planJourney → verwenden bei exakter Zeitangabe ("heute um 14:35", "morgen 07:10")
 * - listJourneys → mehrere Optionen ab jetzt (heute)
 * - listAndPlanJourneys → mehrere Optionen ab angegebenem Zeitpunkt
 * - raw → Rohdaten (Debug / JSON direkt vom Journey-Service)
 * <p>
 * Zeitzone: Europe/Zurich
 * Zeitformat: ISO-8601 mit Offset (z. B. 2025-11-11T14:35:00+01:00)
 */
@Service
@Slf4j
public class TimetableTool {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JourneyService journeys;
    private final PlacesResolver places;

    public TimetableTool(JourneyService journeys, PlacesResolver placesResolver) {
        this.journeys = journeys;
        this.places = placesResolver;
    }


    @Tool(
            name = "datum",
            description = "gibt das heutige DAtum zurück. hilfsmethode"
    )
    public LocalDate datum() {
        return LocalDate.now();
    }

    @Tool(
            name = "nextJourney",
            description = "Nächste Verbindung ab jetzt zwischen Start und Ziel. "
                    + "Verwenden bei Anfragen wie 'naechste Abfahrt', 'jetzt' oder 'bald'. "
                    + "Gibt eine textuelle Beschreibung der Verbindung zurück. Übersetze die Antwort " +
                    "des aufrufs in die sprache des Benutzers die er vorher verwendet hat. achte auf die sprache!"
    )
    public PlanResult nextJourney(
            @ToolParam(description = "Abfahrtsort, z. B. 'Bern'") String origin,
            @ToolParam(description = "Zielort, z. B. 'Zuerich HB'") String destination
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        var when = OffsetDateTime.now(ZURICH);
        return journeys.planJourneyText(originId, destId, when);
    }

    @Tool(
            name = "planJourney",
            description = "Verbindung zu einem angegebenen Zeitpunkt. "
                    + "Verwenden bei Anfragen wie 'heute um 14:35', 'morgen 07:10' oder mit Datum. "
                    + "Der Zeitpunkt muss im ISO-8601-Offset-Format vorliegen."
                    + " Wenn dir das datum fehlt, ruf die operation Datum auf. wenn der benutzer dir die zeit nicht gibt, frag ihn nach abfahrtsdatum. " +
                    "übersetze jeden Text in die Sprache wie der Benutzer mit dir interagiert"
    )
    public PlanResult planJourney(
            @ToolParam(description = "Abfahrtsort, z. B. 'Bern'") String origin,
            @ToolParam(description = "Zielort, z. B. 'Zuerich HB'") String destination,
            @ToolParam(description = "Zeitpunkt im ISO-8601-Format mit Offset, z. B. 2025-11-11T14:35:00+01:00") String datetime
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        var when = parseIsoOffset(datetime);
        return journeys.planJourneyText(originId, destId, when);
    }

    @Tool(
            name = "listJourneys",
            description = "Listet mehrere Verbindungen ab jetzt fuer heute. "
                    + "Verwenden wenn mehrere Vorschlaege erwuenscht sind. reagiere bei Optionen, Möglichkeiten mit dieser Methode. "
                    + "Gibt eine Liste von FlatPlan als JSON-kompatibles Objekt zurueck."
                    + "übersetze jeden Text in die Sprache wie der Benutzer mit dir interagiert"

    )
    public List<FlatPlan> listJourneys(
            @ToolParam(description = "Abfahrtsort, z. B. 'Bern'") String origin,
            @ToolParam(description = "Zielort, z. B. 'Zuerich HB'") String destination,
            @ToolParam(description = "Anzahl der gewuenschten Vorschlaege, Standard 6") Integer limit
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        int max = limit == null || limit < 1 ? 6 : limit;
        var when = OffsetDateTime.now(ZURICH);
        return journeys.planJourneyJson(originId, destId, when, max);
    }

    @Tool(
            name = "listAndPlanJourneys",
            description = "Listet mehrere Verbindungen ab einem angegebenen Zeitpunkt. "
                    + "Verwenden bei Anfragen wie 'heute um 16 Uhr mehrere Verbindungen'. "
                    + " Das Datum muss du, wenn es nicht bekannt ist beim Tool Datum holen gehen. " +
                    " verwende das wenn du mehrere optionen um eine bestimmte zeit darstellen sollst"
                    + "Rueckgabe: Liste von FlatPlan als JSON-kompatibles Objekt."
                    + "übersetze jeden Text in die Sprache wie der Benutzer mit dir interagiert"

    )
    public List<FlatPlan> listAndPlanJourneys(
            @ToolParam(description = "Abfahrtsort, z. B. 'Bern'") String origin,
            @ToolParam(description = "Zielort, z. B. 'Zuerich HB'") String destination,
            @ToolParam(description = "Startzeitpunkt im ISO-Offset-Format, z. B. 2025-11-11T14:35:00+01:00") String datetime,
            @ToolParam(description = "Anzahl der gewuenschten Vorschlaege, Standard 6") Integer limit
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        int max = limit == null || limit < 1 ? 6 : limit;
        var when = parseIsoOffset(datetime);
        return journeys.planJourneyJson(originId, destId, when, max);
    }

    @Tool(
            name = "raw",
            description = "Gibt die unverarbeitete Antwort des Journey-Service als JSON-String zurueck. "
                    + "Verwenden bei Bedarf an exakten API-Feldern, Trip-IDs oder Debug-Informationen. "
                    + "Der Zeitpunkt ist optional."

                    + "übersetze jeden Text in die Sprache wie der Benutzer mit dir interagiert"

    )
    public String raw(
            @ToolParam(description = "Abfahrtsort, z. B. 'Bern'") String origin,
            @ToolParam(description = "Zielort, z. B. 'Zuerich HB'") String destination,
            @ToolParam(description = "Optionaler Startzeitpunkt im ISO-Offset-Format, z. B. 2025-11-11T14:35:00+01:00") String datetime,
            @ToolParam(description = "Anzahl der Alternativen, Standard 6") Integer maxAlternatives
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        var when = (datetime == null || datetime.isBlank())
                ? OffsetDateTime.now(ZURICH)
                : parseIsoOffset(datetime);
        int max = maxAlternatives == null || maxAlternatives < 1 ? 6 : maxAlternatives;
        return journeys.rawTripSearch(originId, destId, when, max);
    }

    private static OffsetDateTime parseIsoOffset(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            throw new IllegalArgumentException("datetime ist Pflicht und muss ISO 8601 mit Offset sein");
        }
        try {
            return OffsetDateTime.parse(datetime, ISO_OFFSET);
        } catch (Exception e) {
            throw new IllegalArgumentException("ungueltiges datetime Format, erwartet ISO 8601 mit Offset z. B. 2025-11-11T14:35:00+01:00");
        }
    }
}
