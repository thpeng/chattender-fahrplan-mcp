package ch.thp.cas.chattenderfahrplan.mapping;

import java.util.List;

public record PlanResult(List<TripOption> options) {
    public static PlanResult of(List<TripOption> opts) { return new PlanResult(opts); }

    public record TripOption(
            String departureTime,
            String arrivalTime,
            String serviceLabel,   // z. B. "S 4"
            String operator,       // z. B. "BLS AG (bls)"
            String fromQuay,
            String toQuay,
            String direction,
            String fromName,       // NEU
            String toName          // NEU
    ) {}
}
