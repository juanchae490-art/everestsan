package com.everest.gear;

public interface UsableGear {
    void onPrimaryAction();
    boolean canAnchorToSteepWall();
    float getStaminaConsumptionRate();
}