package com.hereblingy.hereblingy.setup;

import org.bukkit.Location;

public class SetupConfiguration {
    private final String playerId;
    private Location trashChest;
    private Location keepChest;
    private Location toolSupplyChest;
    private int durabilityThreshold = 10;
    private long createdAt;
    private long lastModified;

    public SetupConfiguration(String playerId) {
        this.playerId = playerId;
        this.createdAt = System.currentTimeMillis();
        this.lastModified = System.currentTimeMillis();
    }

    public String getPlayerId() {
        return playerId;
    }

    public Location getTrashChest() {
        return trashChest;
    }

    public void setTrashChest(Location location) {
        this.trashChest = location;
        this.lastModified = System.currentTimeMillis();
    }

    public Location getKeepChest() {
        return keepChest;
    }

    public void setKeepChest(Location location) {
        this.keepChest = location;
        this.lastModified = System.currentTimeMillis();
    }

    public Location getToolSupplyChest() {
        return toolSupplyChest;
    }

    public void setToolSupplyChest(Location location) {
        this.toolSupplyChest = location;
        this.lastModified = System.currentTimeMillis();
    }

    public int getDurabilityThreshold() {
        return durabilityThreshold;
    }

    public void setDurabilityThreshold(int threshold) {
        this.durabilityThreshold = threshold;
        this.lastModified = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isComplete() {
        return trashChest != null && keepChest != null;
    }

    public void reset() {
        this.trashChest = null;
        this.keepChest = null;
        this.toolSupplyChest = null;
        this.durabilityThreshold = 10;
        this.lastModified = System.currentTimeMillis();
    }
}
