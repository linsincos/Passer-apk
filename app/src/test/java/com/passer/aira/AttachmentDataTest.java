package com.passer.aira;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AttachmentDataTest {
    @Test
    public void distinguishesImageAndTextPayloads() {
        AttachmentData image = AttachmentData.image(
                "photo.png",
                "image/png",
                4,
                "AQIDBA=="
        );
        AttachmentData text = AttachmentData.text(
                "notes.txt",
                "text/plain",
                5,
                "hello"
        );

        assertTrue(image.isImage());
        assertTrue(image.hasImageData());
        assertFalse(image.hasTextContent());
        assertFalse(text.isImage());
        assertFalse(text.hasImageData());
        assertTrue(text.hasTextContent());
    }

    @Test
    public void metadataDoesNotPretendToContainPayload() {
        AttachmentData metadata = AttachmentData.metadata("photo.png", "image/png", 42);

        assertTrue(metadata.isImage());
        assertFalse(metadata.hasImageData());
        assertFalse(metadata.hasTextContent());
    }
}
