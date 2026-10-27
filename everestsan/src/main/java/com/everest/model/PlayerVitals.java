package com.everest.model;

public class PlayerVitals {
    private float hp = 100.0f;
    private float stamina = 100.0f;
    private float water = 100.0f;
    private float bodyTemp = 36.5f;
    private float ambientTemp = -2.0f;
    private float currentAltitude = 5000.0f;

    private int energyBarCount = 3;
    private int waterCount = 3;

    private static final float BASE_ALTITUDE = 5000.0f;
    // 지형 최대 높이(약 240m)에서 정확히 에베레스트 정상(8848m)에 도달하도록 스케일 설정
    private static final float ALTITUDE_SCALE = 16.05f;

    public void updateVitals(float worldY, boolean isMoving, boolean isSprinting,
                             boolean isClimbing, float weatherTempOffset,
                             float staminaDrainMultiplier, float tpf) {
        // 1. 고도 및 외부 기온 계산
        this.currentAltitude = BASE_ALTITUDE + Math.max(0f, worldY * ALTITUDE_SCALE);
        float altitudeDrop = ((this.currentAltitude - BASE_ALTITUDE) / 100.0f) * 0.62f;
        this.ambientTemp = -2.0f - altitudeDrop + weatherTempOffset;

        // 2. 체온 변화 (베이스캠프 인근에서는 보온 회복)
        if (worldY < 14.0f && weatherTempOffset > -8.0f) {
            changeBodyTemp(0.32f * tpf);
        } else {
            float coldSeverity = Math.abs(Math.min(0f, ambientTemp)) * 0.0016f;
            changeBodyTemp(-coldSeverity * tpf);
        }

        // 3. 수분 및 스태미나 소모 / 회복
        float highAltDehydration = (currentAltitude > 7000.0f) ? 1.3f : 1.0f;
        if (isClimbing) {
            consumeStamina(12.5f * staminaDrainMultiplier * tpf);
            consumeWater(2.1f * highAltDehydration * tpf);
        } else if (isMoving && isSprinting) {
            consumeStamina(10.8f * staminaDrainMultiplier * tpf);
            consumeWater(1.8f * highAltDehydration * tpf);
        } else if (isMoving) {
            consumeStamina(2.8f * staminaDrainMultiplier * tpf);
            consumeWater(0.85f * highAltDehydration * tpf);
        } else {
            // 체력(HP)이 낮을수록 스태미나 회복 속도 감소
            float hpRatio = this.hp / 100.0f;
            float hpPenaltyFactor = 0.15f + 0.85f * (hpRatio * hpRatio);
            float hydrationFactor = (water > 15.0f) ? 1.0f : 0.30f;
            float oxygenFactor = (currentAltitude > 7600.0f) ? 0.55f : 1.0f;

            recoverStamina(14.0f * hpPenaltyFactor * hydrationFactor * oxygenFactor * tpf);
            consumeWater(0.30f * tpf);
        }

        // 4. 저체온증 및 탈수 데미지
        if (this.bodyTemp < 35.0f) {
            applyDamage(2.8f * tpf);
        }
        if (this.water <= 0.0f) {
            applyDamage(2.0f * tpf);
        }
    }

    public boolean useEnergyBar() {
        if (energyBarCount <= 0) return false;
        energyBarCount--;
        recoverStamina(60.0f);
        this.hp = Math.min(100.0f, this.hp + 25.0f);
        changeBodyTemp(0.6f);
        return true;
    }

    public boolean useWaterBottle() {
        if (waterCount <= 0) return false;
        waterCount--;
        this.water = Math.min(100.0f, this.water + 70.0f);
        recoverStamina(20.0f);
        changeBodyTemp(0.4f);
        return true;
    }

    public void resupplyAtDepot() {
        this.energyBarCount = 3;
        this.waterCount = 3;
        this.hp = Math.min(100.0f, this.hp + 35.0f);
        this.bodyTemp = Math.min(36.5f, this.bodyTemp + 1.5f);
    }

    public void resetToCamp() {
        this.hp = 100.0f;
        this.stamina = 100.0f;
        this.water = 100.0f;
        this.bodyTemp = 36.5f;
        this.energyBarCount = 3;
        this.waterCount = 3;
    }

    public void consumeStamina(float amount) {
        this.stamina = Math.max(0.0f, this.stamina - Math.max(0f, amount));
    }

    public void recoverStamina(float amount) {
        this.stamina = Math.min(100.0f, this.stamina + Math.max(0f, amount));
    }

    public void consumeWater(float amount) {
        this.water = Math.max(0.0f, this.water - Math.max(0f, amount));
    }

    public void changeBodyTemp(float delta) {
        this.bodyTemp = Math.max(28.0f, Math.min(37.0f, this.bodyTemp + delta));
    }

    public void applyDamage(float damage) {
        this.hp = Math.max(0.0f, this.hp - Math.max(0f, damage));
    }

    public float getHp() { return hp; }
    public float getStamina() { return stamina; }
    public float getWater() { return water; }
    public float getBodyTemp() { return bodyTemp; }
    public float getAmbientTemp() { return ambientTemp; }
    public float getCurrentAltitude() { return currentAltitude; }
    public int getEnergyBarCount() { return energyBarCount; }
    public int getWaterCount() { return waterCount; }
    public boolean isDead() { return hp <= 0.0f; }
}