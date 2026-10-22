package com.amiral.model;

/** Ship orientation: H (horizontal, along a row) or V (vertical, along a column). */
public enum Orientation {
    H, V;

    public Orientation toggle() {
        return this == H ? V : H;
    }
}
