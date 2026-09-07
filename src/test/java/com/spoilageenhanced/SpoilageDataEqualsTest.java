package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 285 regression test: SpoilageData equals/hashCode contract.
 *
 * <p>SpoilageData has a custom equals/hashCode (Pass 91) that short-circuits on primitive
 * fields and uses primitive long comparisons instead of boxed Long.equals on list elements.
 * The semantic contract MUST match the record's auto-generated version: list equality is
 * order-sensitive. This test pins the contract.</p>
 */
public class SpoilageDataEqualsTest {

    @Test
    void equalDataAreEqual() {
        SpoilageData a = new SpoilageData(List.of(1000L, 2000L), List.of(500L), 1, 1.0);
        SpoilageData b = new SpoilageData(List.of(1000L, 2000L), List.of(500L), 1, 1.0);
        assertEquals(a, b, "Equal data must be equal");
        assertEquals(a.hashCode(), b.hashCode(), "Equal data must have equal hashCodes");
    }

    @Test
    void differentFreshExpirationsAreNotEqual() {
        SpoilageData a = new SpoilageData(List.of(1000L, 2000L), List.of(), 0, 1.0);
        SpoilageData b = new SpoilageData(List.of(1000L, 3000L), List.of(), 0, 1.0);
        assertNotEquals(a, b, "Different fresh expirations must not be equal");
    }

    @Test
    void differentStaleExpirationsAreNotEqual() {
        SpoilageData a = new SpoilageData(List.of(), List.of(500L), 0, 1.0);
        SpoilageData b = new SpoilageData(List.of(), List.of(600L), 0, 1.0);
        assertNotEquals(a, b, "Different stale expirations must not be equal");
    }

    @Test
    void differentRottenCountAreNotEqual() {
        SpoilageData a = new SpoilageData(List.of(), List.of(), 1, 1.0);
        SpoilageData b = new SpoilageData(List.of(), List.of(), 2, 1.0);
        assertNotEquals(a, b, "Different rotten counts must not be equal");
    }

    @Test
    void differentSpeedMultiplierAreNotEqual() {
        SpoilageData a = new SpoilageData(List.of(), List.of(), 0, 1.0);
        SpoilageData b = new SpoilageData(List.of(), List.of(), 0, 2.0);
        assertNotEquals(a, b, "Different speed multipliers must not be equal");
    }

    @Test
    void listOrderMatters() {
        SpoilageData a = new SpoilageData(List.of(1000L, 2000L), List.of(), 0, 1.0);
        SpoilageData b = new SpoilageData(List.of(2000L, 1000L), List.of(), 0, 1.0);
        assertNotEquals(a, b, "List order must matter (List.equals is order-sensitive)");
    }

    @Test
    void nullIsNotEqual() {
        SpoilageData a = new SpoilageData(List.of(), List.of(), 0, 1.0);
        assertNotEquals(a, null, "null must not be equal");
    }

    @Test
    void differentTypeIsNotEqual() {
        SpoilageData a = new SpoilageData(List.of(), List.of(), 0, 1.0);
        assertNotEquals(a, "not a SpoilageData", "Different type must not be equal");
    }
}
