package com.mcdg.data;

public enum SignatureHoleType {
    NONE,
    ISLAND_GREEN,
    TUNNEL_GAP,
    DOWNHILL_BOMBER;

    public String displayName() {
        return switch (this) {
            case ISLAND_GREEN -> "Island Green";
            case TUNNEL_GAP -> "Tunnel Gap";
            case DOWNHILL_BOMBER -> "Downhill Bomber";
            case NONE -> "";
        };
    }
}
