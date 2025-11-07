package ch.thp.cas.chattenderfahrplan.mapping;



import java.util.List;

public final class FlatMapper {
    private FlatMapper(){}

    public static FlatPlan toFlat(PlanResult pr) {
        if (pr == null || pr.options() == null) return new FlatPlan(List.of());
        var list = pr.options().stream().map(o ->
                new FlatTrip(
                        nz(o.departureTime()), nz(o.arrivalTime()),
                        nz(o.serviceLabel()),  nz(o.operator()),
                        nz(o.fromQuay()),      nz(o.toQuay()),
                        nz(o.direction())
                )
        ).toList();
        return new FlatPlan(list);
    }

    private static String nz(String s){ return (s==null || s.isBlank()) ? "-" : s; }
}
