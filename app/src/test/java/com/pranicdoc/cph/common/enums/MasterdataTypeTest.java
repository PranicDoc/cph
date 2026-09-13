package com.pranicdoc.cph.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Direct unit coverage for {@link MasterdataType#fromParam(String)} — the
 * single place that owns the request-word ↔ enum-constant mapping used by
 * both {@code MasterDataImportController} and {@code
 * RbacAuthz.hasImportPermission}. Previously this logic lived duplicated
 * inline in the controller and was only exercised indirectly through
 * {@code MasterDataImportControllerAuthorizationTest}'s HTTP-level
 * assertions; this class proves the parsing rules on their own.
 */
class MasterdataTypeTest {

    @Test
    void protocolParsesCorrectly() {
        assertEquals(MasterdataType.PROTOCOL, MasterdataType.fromParam("protocol"));
    }

    @Test
    void colorParsesToPranaColor() {
        assertEquals(MasterdataType.PRANA_COLOR, MasterdataType.fromParam("color"));
    }

    @Test
    void chakraParsesCorrectly() {
        assertEquals(MasterdataType.CHAKRA, MasterdataType.fromParam("chakra"));
    }

    @Test
    void mappingParsesToProtocolColorMapping() {
        assertEquals(MasterdataType.PROTOCOL_COLOR_MAPPING, MasterdataType.fromParam("mapping"));
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertEquals(MasterdataType.PROTOCOL, MasterdataType.fromParam("PROTOCOL"));
        assertEquals(MasterdataType.CHAKRA, MasterdataType.fromParam("Chakra"));
    }

    @Test
    void unknownWordThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MasterdataType.fromParam("bogus"));
        assertEquals("Unknown masterdata type 'bogus'", ex.getMessage());
    }

    @Test
    void enumNameItselfIsNotAcceptedAsTheParamWord() {
        // fromParam accepts the lowercase wire word ("color"), not the enum
        // constant's own name ("PRANA_COLOR") — guards against a future
        // refactor accidentally widening acceptance to raw enum names.
        assertThrows(IllegalArgumentException.class, () -> MasterdataType.fromParam("PRANA_COLOR"));
        assertThrows(IllegalArgumentException.class, () -> MasterdataType.fromParam("protocol_color_mapping"));
    }
}
