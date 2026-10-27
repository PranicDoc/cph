package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed display order of the prana colour catalog: White, EV, Gold, then the
 * light-whitish (Lw), light (L) and medium (M) rows, each in violet, blue, green,
 * yellow, orange, red order. A colour is placed by its code or, failing that, its
 * name ("Light Whitish Violet" and "LWV" are the same slot). Anything not in the
 * list sorts last, by name.
 */
public final class ColorOrder {

    private static final List<String> ORDER = List.of(
            "white", "ev", "gold",
            "lwv", "lwb", "lwg", "lwy", "lwo", "lwr",
            "lv", "lb", "lg", "ly", "lo", "lr",
            "mv", "mb", "mg", "my", "mo", "mr");

    private static final Map<String, Integer> RANK = rank();

    private static final Map<String, String> HUE = Map.of(
            "violet", "v", "blue", "b", "green", "g", "yellow", "y", "orange", "o", "red", "r");

    private static final Pattern SHADE_HUE =
            Pattern.compile("^(light whitish|light|medium)\s+(violet|blue|green|yellow|orange|red)$");

    public static final Comparator<PranaColorCatalog> COMPARATOR = Comparator
            .comparingInt(ColorOrder::rankOf)
            .thenComparing(c -> c.getName() == null ? "" : c.getName().toLowerCase())
            .thenComparing(c -> String.valueOf(c.getCategory()));

    private ColorOrder() {}

    private static Map<String, Integer> rank() {
        java.util.HashMap<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < ORDER.size(); i++) m.put(ORDER.get(i), i);
        return Map.copyOf(m);
    }

    static int rankOf(PranaColorCatalog color) {
        Integer byCode = RANK.get(slot(color.getCode()));
        if (byCode != null) return byCode;
        Integer byName = RANK.get(slot(color.getName()));
        return byName != null ? byName : Integer.MAX_VALUE;
    }

    /** Reduces a code or name to a slot key such as "lwv", or the cleaned text if it isn't one. */
    static String slot(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase().replace('_', ' ').replace('-', ' ')
                .replace("ordinary", "").replace("prana", "").trim().replaceAll("\s+", " ");
        if (s.equals("electric violet")) return "ev";
        Matcher m = SHADE_HUE.matcher(s);
        if (m.matches()) {
            String shade = switch (m.group(1)) {
                case "light whitish" -> "lw";
                case "light" -> "l";
                default -> "m";
            };
            return shade + HUE.get(m.group(2));
        }
        return s;
    }
}
