package com.hereblingy.hereblingy.config;

public class MiningSettings {
    
    public enum Destination {
        KEEP_CHEST,
        TRASH_CHEST,
        INVENTORY;

        public Destination next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private boolean mineEnabled = true;
    private Destination destination = Destination.INVENTORY;

    public MiningSettings() {}

    public MiningSettings(boolean mineEnabled, Destination destination) {
        this.mineEnabled = mineEnabled;
        this.destination = destination;
    }

    public boolean isMineEnabled() {
        return mineEnabled;
    }

    public void setMineEnabled(boolean mineEnabled) {
        this.mineEnabled = mineEnabled;
    }

    public Destination getDestination() {
        return destination;
    }

    public void setDestination(Destination destination) {
        this.destination = destination;
    }
}
