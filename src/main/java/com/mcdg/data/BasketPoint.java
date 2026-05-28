package com.mcdg.data;

public record BasketPoint(int x, int y, int z, int basketHeight) {
    public BasketPoint {
        if (basketHeight < 1) {
            throw new IllegalArgumentException("basketHeight must be >= 1");
        }
    }
}
