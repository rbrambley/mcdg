package com.mcdg.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SignatureHoleTypeTest {
    @Test
    void noneDisplayNameIsEmpty() {
        assertEquals("", SignatureHoleType.NONE.displayName());
    }

    @Test
    void islandGreenDisplayName() {
        assertEquals("Island Green", SignatureHoleType.ISLAND_GREEN.displayName());
    }

    @Test
    void tunnelGapDisplayName() {
        assertEquals("Tunnel Gap", SignatureHoleType.TUNNEL_GAP.displayName());
    }

    @Test
    void downhillBomberDisplayName() {
        assertEquals("Downhill Bomber", SignatureHoleType.DOWNHILL_BOMBER.displayName());
    }

    @Test
    void allEnumValuesHaveDisplayNames() {
        for (SignatureHoleType type : SignatureHoleType.values()) {
            assertNotNull(type.displayName());
        }
    }

    @Test
    void enumHasFourValues() {
        assertEquals(4, SignatureHoleType.values().length);
    }
}
