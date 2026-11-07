package com.pranicdoc.cph.healing.dto;

import java.util.List;
import java.util.Map;

/** The healer's most-used (colour, property) pairs from past protocol understandings, overall and per chakra. */
public record UsualPicksResponse(Picks overall, Map<String, Picks> byChakra) {

    public record Picks(List<Pick> cleansing, List<Pick> energising) {}

    public record Pick(String color, String property) {}
}
