package ch.thp.cas.chattenderfahrplan;

// dto/FlatTrip.java
public record FlatTrip(
        String dep, String arr, String service, String operator,
        String fromQuay, String toQuay, String dir) {}
