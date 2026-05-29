package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for MerchItem — covers the merchandise store feature.
 * <p>
 * MerchItem has no Android or Firebase dependencies in its business logic,
 * so all tests run entirely on the JVM. Note: the 8-arg constructor calls
 * {@link com.google.firebase.Timestamp#now()} which wraps System.currentTimeMillis
 * and does not require Firebase app initialization.
 */
public class MerchItemTest {

    // -------------------------------------------------------------------------
    // Constructor — all fields
    // -------------------------------------------------------------------------

    /**
     * AC: 8-arg constructor stores all provided fields correctly.
     */
    @Test
    public void constructor_setsAllProvidedFields() {
        MerchItem item = makeItem();
        assertEquals("item-1", item.getItemId());
        assertEquals("EcoLUMS T-Shirt", item.getName());
        assertEquals("Organic cotton tee", item.getDescription());
        assertEquals("Apparel", item.getCategory());
        assertEquals(500, item.getPointsCost());
        assertEquals(50, item.getTotalStock());
        assertEquals("👕", item.getImageEmoji());
        assertEquals("admin-uid", item.getAdminId());
    }

    /**
     * AC: availableStock is initialised to totalStock on construction.
     */
    @Test
    public void constructor_setsAvailableStockEqualToTotalStock() {
        MerchItem item = makeItem();
        assertEquals(item.getTotalStock(), item.getAvailableStock());
    }

    /**
     * AC: createdAt is set to a non-null timestamp on construction.
     */
    @Test
    public void constructor_setsCreatedAtNonNull() {
        assertNotNull(makeItem().getCreatedAt());
    }

    /**
     * AC: No-arg constructor does not throw and produces a non-null instance.
     */
    @Test
    public void noArgConstructor_createsNonNullInstance() {
        MerchItem item = new MerchItem();
        assertNotNull(item);
    }

    // -------------------------------------------------------------------------
    // isAvailable — stock checks
    // -------------------------------------------------------------------------

    /**
     * AC: isAvailable returns true when availableStock > 0.
     */
    @Test
    public void isAvailable_withPositiveStock_returnsTrue() {
        MerchItem item = makeItem(); // totalStock = 50, availableStock = 50
        assertTrue(item.isAvailable());
    }

    /**
     * AC: isAvailable returns false when availableStock == 0 (sold out).
     */
    @Test
    public void isAvailable_withZeroStock_returnsFalse() {
        MerchItem item = makeItem();
        item.setAvailableStock(0);
        assertFalse(item.isAvailable());
    }

    /**
     * AC: isAvailable returns false when availableStock is negative
     * (defensive boundary check).
     */
    @Test
    public void isAvailable_withNegativeStock_returnsFalse() {
        MerchItem item = makeItem();
        item.setAvailableStock(-1);
        assertFalse(item.isAvailable());
    }

    /**
     * AC: A single-stock item is available before it is redeemed.
     */
    @Test
    public void isAvailable_singleStockItem_returnsTrue() {
        MerchItem item = new MerchItem("i1", "Cap", "desc", "Apparel", 200, 1, "🧢", "admin");
        assertTrue(item.isAvailable());
    }

    /**
     * AC: After decrementing stock to 0, isAvailable returns false.
     */
    @Test
    public void isAvailable_afterDecrementingToZero_returnsFalse() {
        MerchItem item = new MerchItem("i1", "Cap", "desc", "Apparel", 200, 1, "🧢", "admin");
        item.setAvailableStock(item.getAvailableStock() - 1); // now 0
        assertFalse(item.isAvailable());
    }

    // -------------------------------------------------------------------------
    // Getters and Setters — all fields
    // -------------------------------------------------------------------------

    /**
     * AC: setItemId / getItemId round-trip.
     */
    @Test
    public void itemId_setAndGet() {
        MerchItem item = new MerchItem();
        item.setItemId("new-item-id");
        assertEquals("new-item-id", item.getItemId());
    }

    /**
     * AC: setName / getName round-trip.
     */
    @Test
    public void name_setAndGet() {
        MerchItem item = new MerchItem();
        item.setName("EcoLUMS Bag");
        assertEquals("EcoLUMS Bag", item.getName());
    }

    /**
     * AC: setDescription / getDescription round-trip.
     */
    @Test
    public void description_setAndGet() {
        MerchItem item = new MerchItem();
        item.setDescription("Reusable canvas bag");
        assertEquals("Reusable canvas bag", item.getDescription());
    }

    /**
     * AC: setCategory / getCategory round-trip.
     */
    @Test
    public void category_setAndGet() {
        MerchItem item = new MerchItem();
        item.setCategory("Accessories");
        assertEquals("Accessories", item.getCategory());
    }

    /**
     * AC: setPointsCost / getPointsCost round-trip.
     */
    @Test
    public void pointsCost_setAndGet() {
        MerchItem item = makeItem();
        item.setPointsCost(750);
        assertEquals(750, item.getPointsCost());
    }

    /**
     * AC: setTotalStock / getTotalStock round-trip.
     */
    @Test
    public void totalStock_setAndGet() {
        MerchItem item = makeItem();
        item.setTotalStock(100);
        assertEquals(100, item.getTotalStock());
    }

    /**
     * AC: setAvailableStock / getAvailableStock round-trip.
     */
    @Test
    public void availableStock_setAndGet() {
        MerchItem item = makeItem();
        item.setAvailableStock(25);
        assertEquals(25, item.getAvailableStock());
    }

    /**
     * AC: setImageEmoji / getImageEmoji round-trip.
     */
    @Test
    public void imageEmoji_setAndGet() {
        MerchItem item = new MerchItem();
        item.setImageEmoji("🎒");
        assertEquals("🎒", item.getImageEmoji());
    }

    /**
     * AC: setAdminId / getAdminId round-trip.
     */
    @Test
    public void adminId_setAndGet() {
        MerchItem item = new MerchItem();
        item.setAdminId("different-admin");
        assertEquals("different-admin", item.getAdminId());
    }

    /**
     * AC: No-arg constructor leaves all fields null / 0.
     */
    @Test
    public void noArgConstructor_fieldsAreNullOrZero() {
        MerchItem item = new MerchItem();
        assertNull(item.getItemId());
        assertNull(item.getName());
        assertNull(item.getDescription());
        assertNull(item.getCategory());
        assertEquals(0, item.getPointsCost());
        assertEquals(0, item.getTotalStock());
        assertEquals(0, item.getAvailableStock());
        assertNull(item.getImageEmoji());
        assertNull(item.getAdminId());
    }

    /**
     * AC: pointsCost of zero is valid (free item).
     */
    @Test
    public void pointsCost_zero_isValid() {
        MerchItem item = new MerchItem("i1", "Free Sticker", "desc", "Stickers", 0, 100, "🌿", "admin");
        assertEquals(0, item.getPointsCost());
    }

    /**
     * AC: availableStock and totalStock are independent after construction.
     */
    @Test
    public void availableStock_independentOfTotalStockAfterSetter() {
        MerchItem item = makeItem();
        item.setAvailableStock(10);
        assertEquals(10, item.getAvailableStock());
        assertEquals(50, item.getTotalStock()); // totalStock unchanged
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private MerchItem makeItem() {
        return new MerchItem(
                "item-1",
                "EcoLUMS T-Shirt",
                "Organic cotton tee",
                "Apparel",
                500,
                50,
                "👕",
                "admin-uid"
        );
    }
}
