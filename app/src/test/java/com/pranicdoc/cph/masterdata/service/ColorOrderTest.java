package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColorOrderTest {

    private static PranaColorCatalog color(String code, String name) {
        return PranaColorCatalog.builder().code(code).name(name).category(ColorUsageType.CLEANSING).build();
    }

    @Test
    void ordersByTheFixedSequenceWhetherKeyedByCodeOrName() {
        List<PranaColorCatalog> colors = new ArrayList<>(List.of(
                color("MR", "Medium Red"),
                color("LIGHT_WHITISH_VIOLET", "Light Whitish Violet"),
                color("GOLD", "Gold"),
                color("LB", "Light Blue"),
                color("EV", "Electric Violet"),
                color("ZZ", "Silver"),
                color("WHITE", "White Prana"),
                color("LWR", "Ordinary Light Whitish Red"),
                color("MV", "Medium Violet"),
                color("LV", "Light Violet")));
        Collections.shuffle(colors);

        List<String> sorted = colors.stream().sorted(ColorOrder.COMPARATOR).map(PranaColorCatalog::getName).toList();

        assertThat(sorted).containsExactly(
                "White Prana", "Electric Violet", "Gold",
                "Light Whitish Violet", "Ordinary Light Whitish Red",
                "Light Violet", "Light Blue",
                "Medium Violet", "Medium Red",
                "Silver");
    }

    @Test
    void fullSequenceMatchesTheRequestedOrder() {
        String[] codes = {"white", "ev", "gold", "lwv", "lwb", "lwg", "lwy", "lwo", "lwr",
                "lv", "lb", "lg", "ly", "lo", "lr", "mv", "mb", "mg", "my", "mo", "mr"};
        List<PranaColorCatalog> colors = new ArrayList<>();
        for (String c : codes) colors.add(color(c.toUpperCase(), c));
        Collections.reverse(colors);

        assertThat(colors.stream().sorted(ColorOrder.COMPARATOR).map(PranaColorCatalog::getName).toList())
                .containsExactly(codes);
    }
}
