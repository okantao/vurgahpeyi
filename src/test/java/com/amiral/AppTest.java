package com.amiral;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppTest {

    @Test
    void titleMatchesReferenceUi() {
        assertEquals("BattleShip v1.0", App.TITLE);
    }
}
