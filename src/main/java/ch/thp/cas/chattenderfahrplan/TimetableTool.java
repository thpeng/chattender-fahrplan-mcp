package ch.thp.cas.chattenderfahrplan;

import ch.thp.cas.chattenderfahrplan.journeyservice.JourneyService;
import ch.thp.cas.chattenderfahrplan.journeyservice.PlacesResolver;
import ch.thp.cas.chattenderfahrplan.mapping.FlatPlan;
import ch.thp.cas.chattenderfahrplan.mapping.PlanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Toolset for LLM-based timetable queries (bring-your-own-LLM).
 *
 * Usage guidance for the assistant:
 * - nextJourney → use for “now”, “next departure”, “soon”
 * - planJourney → use for exact departure date/time
 * - listJourneys → multiple options from now (today)
 * - listAndPlanJourneys → multiple options from a given time
 * - raw → raw JSON from the journey service (debug / advanced)
 *
 * Time zone: Europe/Zurich
 * Time format: ISO-8601 with offset, e.g. 2025-11-11T14:35:00+01:00
 */
@Service
@Slf4j
public class TimetableTool {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final String DISCLAIMER_FALLBACK_EN =
            "Please verify this connection on https://www.sbb.ch/, as AI-based interpretation may contain errors.";

    private final JourneyService journeys;
    private final PlacesResolver places;
    private final Map<String, String> disclaimersByLanguage;

    public TimetableTool(JourneyService journeys, PlacesResolver placesResolver) {
        this.journeys = journeys;
        this.places = placesResolver;
        this.disclaimersByLanguage = loadDisclaimers();
    }

    /**
     * Helper tool: returns today's date in Europe/Zurich.
     */
    @Tool(
            name = "datum",
            description = """
Returns today's date in the Europe/Zurich time zone.
Use this when you need an explicit date (e.g. to build an ISO-8601 datetime).
"""
    )
    public LocalDate datum() {
        return LocalDate.now(ZURICH);
    }

    /**
     * Single journey + disclaimer wrapper for LLM.
     */
    public record JourneyResult(
            PlanResult journey,
            String disclaimer
    ) {}

    /**
     * Journey list + disclaimer wrapper for LLM.
     */
    public record JourneyListResult(
            List<FlatPlan> journeys,
            String disclaimer
    ) {}

    @Tool(
            name = "nextJourney",
            description = """
Returns the next connection from now between origin and destination.

Use this for queries like "next departure", "now", or "soon".

CONTRACT FOR ARGUMENTS:
- The 'origin' and 'destination' arguments MUST be Swiss station names written in Latin letters
  (e.g. "Bern", "Zuerich HB", "Zuerich Flughafen", "Jungfraujoch").
- If the user provides station names in another script (e.g. Chinese, Thai, Arabic),
  FIRST translate or transliterate them to the official station name in Latin letters
  before calling this tool.

IMPORTANT FOR THE ASSISTANT:
- Always answer in the same language as the user's last message.
- The optional parameter 'userLanguage' should be set to the user's language (ISO 639-1, e.g. "de", "fr", "en").
- The tool returns a localized disclaimer; keep it in the answer so the user is reminded to verify on https://www.sbb.ch/.
"""
    )
    public JourneyResult nextJourney(
            @ToolParam(description = "Departure location as Swiss station name in Latin letters, e.g. 'Bern', 'Zuerich Flughafen'") String origin,
            @ToolParam(description = "Arrival location as Swiss station name in Latin letters, e.g. 'Zuerich HB', 'Jungfraujoch'") String destination,
            @ToolParam(
                    description = """
Optional user language as ISO 639-1 code (e.g. "de", "fr", "it", "en").
BCP 47 tags like "de-CH" are also accepted and will be normalized to their base language.
If not provided or unknown, the disclaimer will be in English.
"""
            ) String userLanguage
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        var when = OffsetDateTime.now(ZURICH);

        var plan = journeys.planJourneyText(originId, destId, when);
        var disclaimer = resolveDisclaimer(userLanguage);

        return new JourneyResult(plan, disclaimer);
    }

    @Tool(
            name = "planJourney",
            description = """
Returns a journey for a given departure datetime (ISO 8601 with offset).

Use this for queries like "today at 14:35", "tomorrow 07:10", or with an explicit date/time.

CONTRACT FOR ARGUMENTS:
- The 'origin' and 'destination' MUST be Swiss station names written in Latin letters
  (e.g. "Bern", "Zuerich HB", "Zuerich Flughafen", "Jungfraujoch").
  If the user provides other scripts, first translate or transliterate them to Latin station names.
- The 'datetime' argument MUST be an ISO-8601 datetime with offset, e.g. "2025-11-11T14:35:00+01:00".
- If the date is missing, you can call the 'datum' tool to obtain today's date.
- If the user does not provide a time, ask for a departure time.

IMPORTANT FOR THE ASSISTANT:
- Always answer in the same language as the user's last message.
- The optional parameter 'userLanguage' should be set to the user's language (ISO 639-1, e.g. "de", "fr", "en").
- The tool returns a localized disclaimer; keep it in the answer so the user is reminded to verify on https://www.sbb.ch/.
"""
    )
    public JourneyResult planJourney(
            @ToolParam(description = "Departure location as Swiss station name in Latin letters, e.g. 'Bern', 'Zuerich Flughafen'") String origin,
            @ToolParam(description = "Arrival location as Swiss station name in Latin letters, e.g. 'Zuerich HB', 'Jungfraujoch'") String destination,
            @ToolParam(
                    description = """
Departure datetime in ISO 8601 format with offset, e.g. "2025-11-11T14:35:00+01:00".
The datetime MUST include an offset suitable for Europe/Zurich.
"""
            ) String datetime,
            @ToolParam(
                    description = """
Optional user language as ISO 639-1 code (e.g. "de", "fr", "it", "en").
BCP 47 tags like "de-CH" are also accepted and will be normalized to their base language.
If not provided or unknown, the disclaimer will be in English.
"""
            ) String userLanguage
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        var when = parseIsoOffset(datetime);

        var plan = journeys.planJourneyText(originId, destId, when);
        var disclaimer = resolveDisclaimer(userLanguage);

        return new JourneyResult(plan, disclaimer);
    }

    @Tool(
            name = "listJourneys",
            description = """
Lists multiple connections from now for today.

Use this when the user asks for several options or alternative connections.

CONTRACT FOR ARGUMENTS:
- The 'origin' and 'destination' MUST be Swiss station names written in Latin letters
  (e.g. "Bern", "Zuerich HB", "Zuerich Flughafen", "Jungfraujoch").
  If the user provides other scripts, first translate or transliterate them to Latin station names.

Returns a JSON-compatible list of FlatPlan objects plus a disclaimer.

IMPORTANT FOR THE ASSISTANT:
- Always answer in the same language as the user's last message.
- The optional parameter 'userLanguage' should be set to the user's language (ISO 639-1, e.g. "de", "fr", "en").
- The tool returns a localized disclaimer; keep it in the answer so the user is reminded to verify on https://www.sbb.ch/.
"""
    )
    public JourneyListResult listJourneys(
            @ToolParam(description = "Departure location as Swiss station name in Latin letters, e.g. 'Bern', 'Zuerich Flughafen'") String origin,
            @ToolParam(description = "Arrival location as Swiss station name in Latin letters, e.g. 'Zuerich HB', 'Jungfraujoch'") String destination,
            @ToolParam(description = "Number of requested options, default 6") Integer limit,
            @ToolParam(
                    description = """
Optional user language as ISO 639-1 code (e.g. "de", "fr", "it", "en").
BCP 47 tags like "de-CH" are also accepted and will be normalized to their base language.
If not provided or unknown, the disclaimer will be in English.
"""
            ) String userLanguage
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        int max = limit == null || limit < 1 ? 6 : limit;
        var when = OffsetDateTime.now(ZURICH);

        var list = journeys.planJourneyJson(originId, destId, when, max);
        var disclaimer = resolveDisclaimer(userLanguage);

        return new JourneyListResult(list, disclaimer);
    }

    @Tool(
            name = "listAndPlanJourneys",
            description = """
Lists multiple connections starting from a given departure datetime.

Use this for queries like "several options around today 16:00".

CONTRACT FOR ARGUMENTS:
- The 'origin' and 'destination' MUST be Swiss station names written in Latin letters
  (e.g. "Bern", "Zuerich HB", "Zuerich Flughafen", "Jungfraujoch").
  If the user provides other scripts, first translate or transliterate them to Latin station names.
- If the date is unknown, you can call the 'datum' tool to obtain today's date.
- The 'datetime' argument MUST be an ISO-8601 datetime with offset, e.g. "2025-11-11T14:35:00+01:00".

Returns a JSON-compatible list of FlatPlan objects plus a disclaimer.

IMPORTANT FOR THE ASSISTANT:
- Always answer in the same language as the user's last message.
- The optional parameter 'userLanguage' should be set to the user's language (ISO 639-1, e.g. "de", "fr", "en").
- The tool returns a localized disclaimer; keep it in the answer so the user is reminded to verify on https://www.sbb.ch/.
"""
    )
    public JourneyListResult listAndPlanJourneys(
            @ToolParam(description = "Departure location as Swiss station name in Latin letters, e.g. 'Bern', 'Zuerich Flughafen'") String origin,
            @ToolParam(description = "Arrival location as Swiss station name in Latin letters, e.g. 'Zuerich HB', 'Jungfraujoch'") String destination,
            @ToolParam(
                    description = """
Start datetime in ISO 8601 format with offset, e.g. "2025-11-11T14:35:00+01:00".
The datetime MUST include an offset suitable for Europe/Zurich.
"""
            ) String datetime,
            @ToolParam(description = "Number of requested options, default 6") Integer limit,
            @ToolParam(
                    description = """
Optional user language as ISO 639-1 code (e.g. "de", "fr", "it", "en").
BCP 47 tags like "de-CH" are also accepted and will be normalized to their base language.
If not provided or unknown, the disclaimer will be in English.
"""
            ) String userLanguage
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        int max = limit == null || limit < 1 ? 6 : limit;
        var when = parseIsoOffset(datetime);

        var list = journeys.planJourneyJson(originId, destId, when, max);
        var disclaimer = resolveDisclaimer(userLanguage);

        return new JourneyListResult(list, disclaimer);
    }

    @Tool(
            name = "raw",
            description = """
Returns the unprocessed JSON response from the journey service as a string.

Use this when you need exact API fields, trip IDs or debug information.

CONTRACT FOR ARGUMENTS:
- The 'origin' and 'destination' MUST be Swiss station names written in Latin letters
  (e.g. "Bern", "Zuerich HB", "Zuerich Flughafen", "Jungfraujoch").
  If the user provides other scripts, first translate or transliterate them to Latin station names.

The datetime is optional. The response can be large; avoid more than 2 reasoning loops on this.

NOTE:
- This tool does NOT append a disclaimer. The assistant is responsible for adding any safety notes.
"""
    )
    public String raw(
            @ToolParam(description = "Departure location as Swiss station name in Latin letters, e.g. 'Bern', 'Zuerich Flughafen'") String origin,
            @ToolParam(description = "Arrival location as Swiss station name in Latin letters, e.g. 'Zuerich HB', 'Jungfraujoch'") String destination,
            @ToolParam(
                    description = """
Optional start datetime in ISO 8601 format with offset, e.g. "2025-11-11T14:35:00+01:00".
If not provided, 'now' in Europe/Zurich will be used.
"""
            ) String datetime,
            @ToolParam(description = "Number of alternatives, default 6") Integer maxAlternatives
    ) {
        var originId = places.resolveStopPlaceId(origin);
        var destId = places.resolveStopPlaceId(destination);
        var when = (datetime == null || datetime.isBlank())
                ? OffsetDateTime.now(ZURICH)
                : parseIsoOffset(datetime);
        int max = maxAlternatives == null || maxAlternatives < 1 ? 6 : maxAlternatives;
        return journeys.rawTripSearch(originId, destId, when, max);
    }

    // --- helpers -------------------------------------------------------------

    private static OffsetDateTime parseIsoOffset(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            throw new IllegalArgumentException("datetime is required and must be ISO 8601 with offset");
        }
        try {
            return OffsetDateTime.parse(datetime, ISO_OFFSET);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid datetime format, expected ISO 8601 with offset, e.g. 2025-11-11T14:35:00+01:00", e
            );
        }
    }

    /**
     * Load disclaimers from /disclaimers.properties into a language → text map.
     * Keys should be ISO 639-1 codes (e.g. "de", "fr", "en"). BCP-47 like "pt-BR" is fine too.
     */

    private Map<String, String> loadDisclaimers() {
        try (InputStream in = getClass().getResourceAsStream("/disclaimers.properties")) {
            if (in == null) {
                log.warn("disclaimers.properties not found on classpath; falling back to English only.");
                return Map.of();
            }
            Properties props = new Properties();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader); // <- jetzt UTF-8 statt ISO-8859-1
            }
            return props.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            e -> ((String) e.getKey()).trim().toLowerCase(Locale.ROOT),
                            e -> ((String) e.getValue()).trim()
                    ));
        } catch (Exception e) {
            log.warn("Failed to load disclaimers.properties, falling back to English only.", e);
            return Map.of();
        }
    }


    /**
     * Resolve the disclaimer text for the given userLanguage.
     * - Tries exact match (e.g. "de", "fr").
     * - If a BCP-47 tag is given (e.g. "de-CH"), tries the base language ("de").
     * - Falls back to English if nothing matches or language is null/blank.
     */
    private String resolveDisclaimer(String userLanguage) {
        if (userLanguage == null || userLanguage.isBlank()) {
            return DISCLAIMER_FALLBACK_EN;
        }

        String normalized = userLanguage.trim().toLowerCase(Locale.ROOT);
        String disclaimer = disclaimersByLanguage.get(normalized);

        if (disclaimer == null && normalized.contains("-")) {
            String baseLang = normalized.substring(0, normalized.indexOf('-'));
            disclaimer = disclaimersByLanguage.get(baseLang);
        }

        if (disclaimer == null) {
            return DISCLAIMER_FALLBACK_EN;
        }
        return disclaimer;
    }
}
