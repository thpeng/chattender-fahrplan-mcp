package ch.thp.cas.chattenderfahrplan.mapping;

// dto/FlatTrip.java
public record FlatTrip(
        String dep, String arr, String service, String operator,
        String fromQuay, String toQuay, String dir,
        String from, String to
) {}
