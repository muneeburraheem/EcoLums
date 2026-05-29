package com.example.ecolums;

import com.google.firebase.Timestamp;

/**
 * Represents a merchandise item available in the EcoLUMS store.
 * Users redeem green points for physical items (clothing, accessories, etc.).
 * Stored in the Firestore {@code merch} collection.
 */
public class MerchItem {

    private String itemId;
    private String name;
    private String description;
    private String category;
    private int pointsCost;
    private int totalStock;
    private int availableStock;
    private String imageEmoji;
    private String adminId;
    private Timestamp createdAt;

    public MerchItem() {}

    public MerchItem(String itemId, String name, String description,
                     String category, int pointsCost, int totalStock,
                     String imageEmoji, String adminId) {
        this.itemId = itemId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.pointsCost = pointsCost;
        this.totalStock = totalStock;
        this.availableStock = totalStock;
        this.imageEmoji = imageEmoji;
        this.adminId = adminId;
        this.createdAt = Timestamp.now();
    }

    public boolean isAvailable() {
        return availableStock > 0;
    }

    // Getters & Setters
    public String getItemId() { return itemId; }
    public void setItemId(String v) { itemId = v; }

    public String getName() { return name; }
    public void setName(String v) { name = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }

    public String getCategory() { return category; }
    public void setCategory(String v) { category = v; }

    public int getPointsCost() { return pointsCost; }
    public void setPointsCost(int v) { pointsCost = v; }

    public int getTotalStock() { return totalStock; }
    public void setTotalStock(int v) { totalStock = v; }

    public int getAvailableStock() { return availableStock; }
    public void setAvailableStock(int v) { availableStock = v; }

    public String getImageEmoji() { return imageEmoji; }
    public void setImageEmoji(String v) { imageEmoji = v; }

    public String getAdminId() { return adminId; }
    public void setAdminId(String v) { adminId = v; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp v) { createdAt = v; }
}
