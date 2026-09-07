package com.spoilageenhanced;

import com.spoilageenhanced.network.SpoilagePayloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 164 regression test: SpoilagePayloads.appendType.
 *
 * The payload registration mixins (Fabric/Forge/NeoForge) all call this helper to
 * inject their payload types into the codec table. A bug here would silently
 * prevent the payload from being registered on one or more loaders, breaking the
 * HUD block-spoilage query on that loader.
 *
 * Contract: appendType returns a NEW list with the entry appended. The input list
 * is never mutated (the mixin replaces the argument with the returned value).
 */
public class SpoilagePayloadsTest {

    @Test
    void appendTypeReturnsNewListWithEntry() {
        List<Object> list = new ArrayList<>();
        Object result = SpoilagePayloads.appendType(list, makeType("test:payload"), makeCodec());

        assertNotSame(list, result, "appendType must return a NEW list, not the input");
        assertTrue(result instanceof List, "The result must be a list");
        List<?> extended = (List<?>) result;
        assertEquals(1, extended.size(), "One entry must be in the returned list");
        assertTrue(extended.get(0) instanceof CustomPacketPayload.TypeAndCodec,
                "The added entry must be a TypeAndCodec");
        assertEquals(0, list.size(), "The input list must NOT be mutated");
    }

    @Test
    void appendTypePreservesExistingEntries() {
        // Start with a list whose first element is a TypeAndCodec so the helper recognises it
        List<Object> list = new ArrayList<>();
        CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, CustomPacketPayload> existing =
                new CustomPacketPayload.TypeAndCodec<>(makeType("test:existing"), makeCodec());
        list.add(existing);

        Object result = SpoilagePayloads.appendType(list, makeType("test:payload"), makeCodec());

        List<?> extended = (List<?>) result;
        assertEquals(2, extended.size(), "Existing entries must be preserved in the new list");
        assertSame(existing, extended.get(0), "The first entry must be the existing TypeAndCodec");
        assertTrue(extended.get(1) instanceof CustomPacketPayload.TypeAndCodec,
                "The second entry must be the newly appended TypeAndCodec");
    }

    @Test
    void appendTypeReturnsOriginalForNonList() {
        String notAList = "not a list";
        Object result = SpoilagePayloads.appendType(notAList, makeType("test:payload"), makeCodec());

        assertSame(notAList, result, "Non-list arguments must be returned unchanged");
    }

    @Test
    void appendTypeReturnsOriginalForWrongElementType() {
        List<String> stringList = new ArrayList<>();
        stringList.add("not a TypeAndCodec");
        Object result = SpoilagePayloads.appendType(stringList, makeType("test:payload"), makeCodec());

        assertSame(stringList, result, "List with wrong element type must be returned unchanged");
        assertEquals(1, stringList.size(), "No entry must be added");
    }

    @Test
    void appendTypeWorksWithEmptyList() {
        List<Object> list = new ArrayList<>();
        Object result = SpoilagePayloads.appendType(list, makeType("test:payload"), makeCodec());

        List<?> extended = (List<?>) result;
        assertEquals(1, extended.size());
        assertTrue(extended.get(0) instanceof CustomPacketPayload.TypeAndCodec);
    }

    @Test
    void appendTypeWorksWithNullList() {
        // The method checks instanceof List, so null is not a List
        Object result = SpoilagePayloads.appendType(null, makeType("test:payload"), makeCodec());
        assertNull(result, "null must be returned unchanged");
    }

    @Test
    void multipleAppendsAccumulate() {
        List<Object> list = new ArrayList<>();
        list = (List<Object>) SpoilagePayloads.appendType(list, makeType("test:one"), makeCodec());
        list = (List<Object>) SpoilagePayloads.appendType(list, makeType("test:two"), makeCodec());
        list = (List<Object>) SpoilagePayloads.appendType(list, makeType("test:three"), makeCodec());

        assertEquals(3, list.size(), "Chained appends must accumulate in the returned lists");
    }

    @SuppressWarnings("unchecked")
    private static CustomPacketPayload.Type<CustomPacketPayload> makeType(String id) {
        return new CustomPacketPayload.Type<>(Identifier.parse(id));
    }

    private static StreamCodec<FriendlyByteBuf, CustomPacketPayload> makeCodec() {
        return CustomPacketPayload.codec(
                (payload, buf) -> {},
                buf -> new CustomPacketPayload() {
                    @Override
                    public Type<? extends CustomPacketPayload> type() {
                        return makeType("test:dummy");
                    }
                }
        );
    }
}