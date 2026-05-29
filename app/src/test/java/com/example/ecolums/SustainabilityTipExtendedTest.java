package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;

/**
 * Extended unit tests for SustainabilityTip — covers externalLinks, lastEditedAt,
 * no-arg constructor, and fields not covered in the primary SustainabilityTipTest.
 * <p>
 * SustainabilityTip is a pure-Java model. Timestamp is constructed directly
 * from epoch seconds and does not require Firebase initialization.
 */
public class SustainabilityTipExtendedTest {

    // -------------------------------------------------------------------------
    // No-arg constructor
    // -------------------------------------------------------------------------

    /**
     * AC: No-arg constructor creates a non-null instance with all fields null/0/false.
     */
    @Test
    public void noArgConstructor_allFieldsAreDefault() {
        SustainabilityTip tip = new SustainabilityTip();
        assertNull(tip.getTipId());
        assertNull(tip.getTitle());
        assertNull(tip.getSummary());
        assertNull(tip.getBodyContent());
        assertNull(tip.getCategory());
        assertNull(tip.getImageUrls());
        assertNull(tip.getVideoUrl());
        assertNull(tip.getExternalLinks());
        assertNull(tip.getAuthorAdminId());
        assertNull(tip.getPublishedAt());
        assertNull(tip.getLastEditedAt());
        assertEquals(0, tip.getViewCount());
        assertEquals(0, tip.getLikeCount());
        assertFalse(tip.isNew());
    }

    // -------------------------------------------------------------------------
    // externalLinks
    // -------------------------------------------------------------------------

    /**
     * AC: externalLinks is null by default (not set in 7-arg constructor).
     */
    @Test
    public void externalLinks_defaultNull() {
        assertNull(makeTip().getExternalLinks());
    }

    /**
     * AC: externalLinks can be set and retrieved.
     */
    @Test
    public void externalLinks_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setExternalLinks(Arrays.asList(
                "https://www.epa.gov/greenvehicles",
                "https://www.carbonfootprint.com"
        ));
        assertEquals(2, tip.getExternalLinks().size());
        assertTrue(tip.getExternalLinks().contains("https://www.epa.gov/greenvehicles"));
    }

    /**
     * AC: externalLinks can be set to an empty list.
     */
    @Test
    public void externalLinks_emptyList_isAllowed() {
        SustainabilityTip tip = makeTip();
        tip.setExternalLinks(Arrays.asList());
        assertTrue(tip.getExternalLinks().isEmpty());
    }

    /**
     * AC: externalLinks can be cleared by setting null.
     */
    @Test
    public void externalLinks_setToNull_afterAssignment() {
        SustainabilityTip tip = makeTip();
        tip.setExternalLinks(Arrays.asList("https://example.com"));
        tip.setExternalLinks(null);
        assertNull(tip.getExternalLinks());
    }

    // -------------------------------------------------------------------------
    // lastEditedAt
    // -------------------------------------------------------------------------

    /**
     * AC: lastEditedAt is null when first published (no edits yet).
     */
    @Test
    public void lastEditedAt_defaultNull() {
        assertNull(makeTip().getLastEditedAt());
    }

    /**
     * AC: lastEditedAt can be set to a timestamp after an admin edit.
     */
    @Test
    public void lastEditedAt_setAndGet() {
        SustainabilityTip tip = makeTip();
        Timestamp ts = new Timestamp(1_705_000_000L, 0);
        tip.setLastEditedAt(ts);
        assertEquals(ts, tip.getLastEditedAt());
    }

    /**
     * AC: lastEditedAt should be after publishedAt for an edited tip.
     */
    @Test
    public void lastEditedAt_afterPublishedAt_isConsistent() {
        Timestamp published = new Timestamp(1_700_000_000L, 0);
        Timestamp edited = new Timestamp(1_705_000_000L, 0);
        SustainabilityTip tip = new SustainabilityTip(
                "t1", "Title", "Summary", "Body",
                SustainabilityTip.CATEGORY_GENERAL, "admin", published
        );
        tip.setLastEditedAt(edited);
        assertTrue(tip.getLastEditedAt().toDate().after(tip.getPublishedAt().toDate()));
    }

    // -------------------------------------------------------------------------
    // Engagement counters
    // -------------------------------------------------------------------------

    /**
     * AC: viewCount can be incremented multiple times.
     */
    @Test
    public void viewCount_incrementedMultipleTimes() {
        SustainabilityTip tip = makeTip();
        tip.setViewCount(tip.getViewCount() + 1);
        tip.setViewCount(tip.getViewCount() + 1);
        tip.setViewCount(tip.getViewCount() + 1);
        assertEquals(3, tip.getViewCount());
    }

    /**
     * AC: likeCount can be incremented and queried.
     */
    @Test
    public void likeCount_incrementedAndQueried() {
        SustainabilityTip tip = makeTip();
        for (int i = 0; i < 5; i++) tip.setLikeCount(tip.getLikeCount() + 1);
        assertEquals(5, tip.getLikeCount());
    }

    /**
     * AC: likeCount can be decremented (e.g., unlike action).
     */
    @Test
    public void likeCount_decremented_afterUnlike() {
        SustainabilityTip tip = makeTip();
        tip.setLikeCount(10);
        tip.setLikeCount(tip.getLikeCount() - 1);
        assertEquals(9, tip.getLikeCount());
    }

    // -------------------------------------------------------------------------
    // Image URLs
    // -------------------------------------------------------------------------

    /**
     * AC: imageUrls can hold an empty list.
     */
    @Test
    public void imageUrls_emptyList_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setImageUrls(Arrays.asList());
        assertTrue(tip.getImageUrls().isEmpty());
    }

    /**
     * AC: imageUrls can contain multiple entries.
     */
    @Test
    public void imageUrls_multipleEntries_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setImageUrls(Arrays.asList("https://img1.png", "https://img2.png", "https://img3.png"));
        assertEquals(3, tip.getImageUrls().size());
    }

    // -------------------------------------------------------------------------
    // Category — all values
    // -------------------------------------------------------------------------

    /**
     * AC: A tip's category can be any of the four defined categories.
     */
    @Test
    public void category_allFourValues_roundTrip() {
        SustainabilityTip tip = makeTip();
        for (String cat : new String[]{
                SustainabilityTip.CATEGORY_TRANSPORT,
                SustainabilityTip.CATEGORY_ENERGY,
                SustainabilityTip.CATEGORY_WASTE,
                SustainabilityTip.CATEGORY_GENERAL}) {
            tip.setCategory(cat);
            assertEquals(cat, tip.getCategory());
        }
    }

    // -------------------------------------------------------------------------
    // Setter round-trips
    // -------------------------------------------------------------------------

    @Test
    public void tipId_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setTipId("new-tip-id");
        assertEquals("new-tip-id", tip.getTipId());
    }

    @Test
    public void title_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setTitle("Updated Title");
        assertEquals("Updated Title", tip.getTitle());
    }

    @Test
    public void summary_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setSummary("Updated summary text.");
        assertEquals("Updated summary text.", tip.getSummary());
    }

    @Test
    public void bodyContent_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setBodyContent("Updated full body content.");
        assertEquals("Updated full body content.", tip.getBodyContent());
    }

    @Test
    public void authorAdminId_setAndGet() {
        SustainabilityTip tip = makeTip();
        tip.setAuthorAdminId("new-admin-uid");
        assertEquals("new-admin-uid", tip.getAuthorAdminId());
    }

    @Test
    public void publishedAt_setAndGet() {
        SustainabilityTip tip = makeTip();
        Timestamp newTs = new Timestamp(1_710_000_000L, 0);
        tip.setPublishedAt(newTs);
        assertEquals(newTs, tip.getPublishedAt());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private SustainabilityTip makeTip() {
        return new SustainabilityTip(
                "tip-1",
                "Ride Your Bike",
                "Cycling reduces CO2",
                "Full body...",
                SustainabilityTip.CATEGORY_TRANSPORT,
                "admin-uid",
                new Timestamp(1_700_000_000L, 0)
        );
    }
}
