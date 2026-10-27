package com.everest.controller;

import com.everest.gear.ClimbingGear;
import com.everest.gear.EnergyBarGear;
import com.everest.gear.WaterBottleGear;
import com.everest.model.PlayerVitals;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

public class FirstPersonClimberControl extends BetterCharacterControl {
    private final Camera cam;
    private final Node terrainNode;
    private final PlayerVitals vitals;
    private ClimbingGear currentGear;
    private String statusMessage = "";
    private float messageTimer = 0.0f;

    private float yaw = FastMath.PI;
    private float pitch = -0.12f; // 시작 시 정면 설산을 올려다보는 각도
    private static final float MAX_PITCH = FastMath.DEG_TO_RAD * 78.0f;
    private final Quaternion lookRotation = new Quaternion();
    private final Quaternion bodyYawRotation = new Quaternion();

    // 1인칭 전신(상체 파카 + 하네스 + 골반 + 다리 + 등산화) 노드
    private final Node lowerBodyNode = new Node("PlayerFullBody");
    private final Node leftLegPivot = new Node("LeftLegPivot");
    private final Node rightLegPivot = new Node("RightLegPivot");
    private final Quaternion leftLegRot = new Quaternion();
    private final Quaternion rightLegRot = new Quaternion();

    private boolean forward, backward, left, right, sprint, actionHold;
    private boolean isClimbingWall = false;
    private boolean isMantling = false;
    private float mantleTimer = 0.0f;
    private static final float MANTLE_DURATION = 0.46f;

    private float currentSlopeDeg = 0.0f;
    private final Vector3f groundNormal = new Vector3f(0, 1, 0);
    private float headBobTimer = 0.0f;
    private float weatherTempOffset = 0.0f;

    private float lastVerticalVelocity = 0.0f;
    private boolean wasOnGround = true;

    private final Vector3f walkDirection = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camLeft = new Vector3f();
    private final Vector3f eyePos = new Vector3f();
    private final Vector3f slideDir = new Vector3f();
    private final Vector3f mantleStartPos = new Vector3f();
    private final Vector3f mantleTargetPos = new Vector3f();
    private final Vector3f normalGravity = new Vector3f(0, -22.0f, 0);
    private final Vector3f zeroGravity = new Vector3f(0, 0, 0);

    private final Ray probeRay = new Ray(new Vector3f(), new Vector3f());
    private final CollisionResults rayResults = new CollisionResults();

    public FirstPersonClimberControl(Camera cam, Node terrainNode, Node rootNode,
                                     AssetManager assetManager, PlayerVitals vitals, ClimbingGear initialGear) {
        super(0.42f, 1.8f, 75.0f);
        this.cam = cam;
        this.terrainNode = terrainNode;
        this.vitals = vitals;
        this.currentGear = initialGear;
        this.setJumpForce(new Vector3f(0, 385f, 0));

        buildConnectedFirstPersonBody(assetManager);
        rootNode.attachChild(lowerBodyNode);
    }

    // 아래를 내려다볼 때 원기둥 단면이 아닌 실제 상체(다운파카 가슴/복부), 안전벨트, 골반, 다리, 둥근 등산화가 이어져 보이도록 구성
    private void buildConnectedFirstPersonBody(AssetManager assetManager) {
        Material parkaOrangeMat = makeLitMat(assetManager, new ColorRGBA(0.90f, 0.48f, 0.12f, 1.0f));
        Material parkaRedMat = makeLitMat(assetManager, new ColorRGBA(0.82f, 0.18f, 0.15f, 1.0f));
        Material darkVestMat = makeLitMat(assetManager, new ColorRGBA(0.16f, 0.18f, 0.22f, 1.0f));
        Material pantsMat = makeLitMat(assetManager, new ColorRGBA(0.22f, 0.25f, 0.30f, 1.0f));
        Material harnessMat = makeLitMat(assetManager, new ColorRGBA(0.12f, 0.13f, 0.15f, 1.0f));
        Material metalClipMat = makeLitMat(assetManager, new ColorRGBA(0.78f, 0.84f, 0.90f, 1.0f));
        Material bootLeatherMat = makeLitMat(assetManager, new ColorRGBA(0.28f, 0.20f, 0.14f, 1.0f));

        // 1. 상체 가슴 & 복부 (오렌지/레드 원정대 우모복 파카 몸통 - 고개를 숙이면 가장 먼저 보임)
        Geometry upperChest = new Geometry("UpperChest", new Sphere(14, 14, 0.22f));
        upperChest.setLocalScale(1.15f, 1.15f, 0.72f);
        upperChest.setMaterial(parkaOrangeMat);
        upperChest.setLocalTranslation(0f, 1.26f, -0.09f);
        lowerBodyNode.attachChild(upperChest);

        Geometry midTorso = new Geometry("MidTorso", new Cylinder(14, 14, 0.20f, 0.36f, true));
        midTorso.setLocalScale(1.1f, 0.72f, 1.0f);
        midTorso.setMaterial(parkaOrangeMat);
        midTorso.setLocalTranslation(0f, 1.08f, -0.08f);
        midTorso.rotate(FastMath.HALF_PI, 0f, 0f);
        lowerBodyNode.attachChild(midTorso);

        // 파카 전면 중앙 방풍 지퍼 라인 & 가슴 포켓 패치
        Geometry centerZipper = new Geometry("CenterZipper", new Box(0.022f, 0.24f, 0.02f));
        centerZipper.setMaterial(darkVestMat);
        centerZipper.setLocalTranslation(0f, 1.14f, 0.06f);
        centerZipper.rotate(0.12f, 0f, 0f);
        lowerBodyNode.attachChild(centerZipper);

        // 2. 허리 클라이밍 하네스(안전벨트) & 금속 카라비너 고리
        Geometry waistHarness = new Geometry("WaistHarness", new Cylinder(14, 14, 0.21f, 0.08f, true));
        waistHarness.setLocalScale(1.08f, 0.74f, 1.0f);
        waistHarness.setMaterial(harnessMat);
        waistHarness.setLocalTranslation(0f, 0.91f, -0.07f);
        waistHarness.rotate(FastMath.HALF_PI, 0f, 0f);
        lowerBodyNode.attachChild(waistHarness);

        Geometry beltBuckle = new Geometry("BeltBuckle", new Box(0.045f, 0.035f, 0.02f));
        beltBuckle.setMaterial(metalClipMat);
        beltBuckle.setLocalTranslation(0f, 0.91f, 0.085f);
        lowerBodyNode.attachChild(beltBuckle);

        // 3. 골반/엉덩이 연결부 (다리 원기둥 윗부분이 절대 뚫려 보이지 않도록 덮어줌)
        Geometry pelvis = new Geometry("Pelvis", new Sphere(14, 14, 0.20f));
        pelvis.setLocalScale(1.12f, 0.75f, 0.72f);
        pelvis.setMaterial(pantsMat);
        pelvis.setLocalTranslation(0f, 0.82f, -0.06f);
        lowerBodyNode.attachChild(pelvis);

        // 4. 양쪽 다리 관절 피벗 (허벅지 -> 무릎 구체 -> 종아리 스패츠 -> 둥근 등산 부츠)
        leftLegPivot.setLocalTranslation(0.115f, 0.82f, -0.04f);
        rightLegPivot.setLocalTranslation(-0.115f, 0.82f, -0.04f);
        lowerBodyNode.attachChild(leftLegPivot);
        lowerBodyNode.attachChild(rightLegPivot);

        Node[] pivots = {leftLegPivot, rightLegPivot};
        for (int i = 0; i < 2; i++) {
            Node leg = pivots[i];

            // 허벅지 하네스 레그루프 밴드
            Geometry legLoop = new Geometry("LegLoop_" + i, new Cylinder(12, 12, 0.098f, 0.045f, true));
            legLoop.setMaterial(harnessMat);
            legLoop.setLocalTranslation(0f, -0.12f, 0.02f);
            legLoop.rotate(FastMath.HALF_PI - 0.1f, 0f, 0f);
            leg.attachChild(legLoop);

            // 허벅지 (고어텍스 방한 바지)
            Geometry thigh = new Geometry("Thigh_" + i, new Cylinder(14, 14, 0.092f, 0.36f, true));
            thigh.setMaterial(pantsMat);
            thigh.setLocalTranslation(0f, -0.20f, 0.025f);
            thigh.rotate(FastMath.HALF_PI - 0.08f, 0f, 0f);
            leg.attachChild(thigh);

            // 무릎 보호대 관절 (Sphere로 자연스럽게 연결)
            Geometry knee = new Geometry("Knee_" + i, new Sphere(12, 12, 0.088f));
            knee.setMaterial(darkVestMat);
            knee.setLocalTranslation(0f, -0.39f, 0.045f);
            leg.attachChild(knee);

            // 정강이 & 붉은색 방설 스패츠 (Gaiter)
            Geometry shinGaiter = new Geometry("Gaiter_" + i, new Cylinder(14, 14, 0.082f, 0.34f, true));
            shinGaiter.setMaterial(parkaRedMat);
            shinGaiter.setLocalTranslation(0f, -0.55f, 0.035f);
            shinGaiter.rotate(FastMath.HALF_PI, 0f, 0f);
            leg.attachChild(shinGaiter);

            // 등산화 발목 패딩 커프스
            Geometry ankleCuff = new Geometry("AnkleCuff_" + i, new Cylinder(12, 12, 0.078f, 0.10f, true));
            ankleCuff.setMaterial(darkVestMat);
            ankleCuff.setLocalTranslation(0f, -0.71f, 0.045f);
            ankleCuff.rotate(FastMath.HALF_PI, 0f, 0f);
            leg.attachChild(ankleCuff);

            // 둥근 코 등산화 몸체 (네모난 Box 대신 타원형 Sphere로 자연스러운 신발 앞코 구현!)
            Geometry bootBody = new Geometry("BootBody_" + i, new Sphere(14, 14, 0.095f));
            bootBody.setLocalScale(0.88f, 0.65f, 1.55f);
            bootBody.setMaterial(bootLeatherMat);
            bootBody.setLocalTranslation(0f, -0.76f, 0.10f);
            leg.attachChild(bootBody);

            // 등산화 앞코 검은색 고무 랜드(Toe Bumper)
            Geometry bootToe = new Geometry("BootToe_" + i, new Sphere(12, 12, 0.082f));
            bootToe.setLocalScale(0.90f, 0.58f, 1.15f);
            bootToe.setMaterial(harnessMat);
            bootToe.setLocalTranslation(0f, -0.77f, 0.155f);
            leg.attachChild(bootToe);

            // 강철 아이젠(Crampon) 앞발톱 포인트 2개
            for (float cx : new float[]{-0.028f, 0.028f}) {
                Geometry spike = new Geometry("CramponSpike_" + i, new Box(0.008f, 0.008f, 0.035f));
                spike.setMaterial(metalClipMat);
                spike.setLocalTranslation(cx, -0.80f, 0.245f);
                leg.attachChild(spike);
            }
        }
    }

    private Material makeLitMat(AssetManager assetManager, ColorRGBA color) {
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", color);
        mat.setColor("Ambient", color.mult(0.85f));
        return mat;
    }

    public void rotateCamera(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch = FastMath.clamp(pitch + deltaPitch, -MAX_PITCH, MAX_PITCH);
        lookRotation.fromAngles(pitch, yaw, 0f);
        cam.setRotation(lookRotation);
    }

    @Override
    public void update(float tpf) {
        super.update(tpf);
        if (messageTimer > 0) {
            messageTimer -= tpf;
            if (messageTimer <= 0) statusMessage = "";
        }

        if (spatial == null || vitals.isDead()) {
            setWalkDirection(Vector3f.ZERO);
            return;
        }

        boolean currentOnGround = isOnGround();
        if (!wasOnGround && currentOnGround) {
            if (lastVerticalVelocity < -14.5f) {
                float fallDamage = (Math.abs(lastVerticalVelocity) - 12.0f) * 3.8f;
                vitals.applyDamage(fallDamage);
                showNotice("추락 부상 (-" + (int) fallDamage + " HP)");
            }
        }
        wasOnGround = currentOnGround;
        lastVerticalVelocity = getVelocity().y;

        if (isMantling) {
            updateMantleMotion(tpf);
            return;
        }

        checkGroundSlope();
        checkWallClimbing();

        lookRotation.fromAngles(pitch, yaw, 0f);
        cam.setRotation(lookRotation);

        bodyYawRotation.fromAngles(0f, yaw, 0f);
        bodyYawRotation.getRotationColumn(2, camDir).normalizeLocal();
        bodyYawRotation.getRotationColumn(0, camLeft).normalizeLocal();
        walkDirection.set(0, 0, 0);

        boolean isMoving = (forward || backward || left || right);
        boolean isSprintingNow = isMoving && sprint && !isClimbingWall && vitals.getStamina() > 8.0f;

        if (isClimbingWall) {
            float climbSpeed = (forward ? 4.5f : (backward ? -2.8f : 0f));
            this.setGravity(zeroGravity);
            this.setWalkDirection(camDir.mult(1.8f));
            Vector3f pos = spatial.getLocalTranslation();
            this.warp(eyePos.set(pos.x + camDir.x * 0.04f, pos.y + climbSpeed * tpf, pos.z + camDir.z * 0.04f));
        } else {
            this.setGravity(normalGravity);
            if (forward) walkDirection.addLocal(camDir);
            if (backward) walkDirection.addLocal(camDir.negate());
            if (left) walkDirection.addLocal(camLeft);
            if (right) walkDirection.addLocal(camLeft.negate());

            float baseSpeed = (vitals.getStamina() > 5.0f) ? 7.6f : 3.6f;
            if (isSprintingNow) baseSpeed *= 1.65f;

            float maxWalkableSlope = currentGear.canAnchorToSteepWall() ? 68.0f : 56.0f;

            if (currentSlopeDeg > maxWalkableSlope) {
                slideDir.set(groundNormal.x, 0, groundNormal.z).normalizeLocal().multLocal(4.0f);
                walkDirection.normalizeLocal().multLocal(baseSpeed * 0.45f).addLocal(slideDir);
            } else {
                walkDirection.normalizeLocal().multLocal(baseSpeed);
            }
            setWalkDirection(walkDirection);
        }

        vitals.updateVitals(
                spatial.getLocalTranslation().y,
                isMoving,
                isSprintingNow,
                isClimbingWall,
                weatherTempOffset,
                currentGear.getStaminaConsumptionRate(),
                tpf
        );

        Vector3f playerPos = spatial.getLocalTranslation();
        eyePos.set(playerPos).addLocal(0, 1.62f, 0);
        if (isMoving && !isClimbingWall) {
            headBobTimer += tpf * (isSprintingNow ? 13.5f : 8.2f);
            eyePos.y += FastMath.sin(headBobTimer) * (isSprintingNow ? 0.055f : 0.030f);
        }
        cam.setLocation(eyePos);

        lowerBodyNode.setLocalTranslation(playerPos);
        lowerBodyNode.setLocalRotation(bodyYawRotation);
        float legSwing = isMoving ? FastMath.sin(headBobTimer) * (isSprintingNow ? 0.52f : 0.34f) : 0f;
        leftLegRot.fromAngles(legSwing, 0f, 0f);
        rightLegRot.fromAngles(-legSwing, 0f, 0f);
        leftLegPivot.setLocalRotation(leftLegRot);
        rightLegPivot.setLocalRotation(rightLegRot);

        currentGear.updateViewModel(tpf, isMoving, isSprintingNow, false, 0f);
    }

    public void showNotice(String msg) {
        this.statusMessage = msg;
        this.messageTimer = 3.0f;
    }

    private boolean tryStartMantle() {
        if (vitals.getStamina() < 8.0f) return false;
        Vector3f playerPos = spatial.getLocalTranslation();
        bodyYawRotation.getRotationColumn(2, camDir).normalizeLocal();

        boolean wallAhead = false;
        for (float hOffset : new float[]{0.65f, 1.35f}) {
            rayResults.clear();
            probeRay.setOrigin(eyePos.set(playerPos.x, playerPos.y + hOffset, playerPos.z));
            probeRay.setDirection(camDir);
            terrainNode.collideWith(probeRay, rayResults);
            if (rayResults.size() > 0 && rayResults.getClosestCollision().getDistance() <= 2.6f) {
                wallAhead = true;
                break;
            }
        }
        if (!wallAhead) return false;

        rayResults.clear();
        probeRay.setOrigin(eyePos.set(playerPos.x, playerPos.y + 3.7f, playerPos.z));
        probeRay.setDirection(camDir);
        terrainNode.collideWith(probeRay, rayResults);
        if (rayResults.size() > 0 && rayResults.getClosestCollision().getDistance() <= 2.2f) return false;

        rayResults.clear();
        probeRay.setOrigin(eyePos.set(playerPos.x + camDir.x * 1.4f, playerPos.y + 3.8f, playerPos.z + camDir.z * 1.4f));
        probeRay.setDirection(Vector3f.UNIT_Y.negate());
        terrainNode.collideWith(probeRay, rayResults);

        if (rayResults.size() > 0) {
            CollisionResult ledgeHit = rayResults.getClosestCollision();
            float ledgeY = ledgeHit.getContactPoint().y;
            float heightDiff = ledgeY - playerPos.y;

            if (heightDiff >= 0.7f && heightDiff <= 3.6f) {
                isMantling = true;
                mantleTimer = 0.0f;
                mantleStartPos.set(playerPos);
                mantleTargetPos.set(playerPos.x + camDir.x * 1.45f, ledgeY + 0.2f, playerPos.z + camDir.z * 1.45f);
                vitals.consumeStamina(8.0f);
                setWalkDirection(Vector3f.ZERO);
                return true;
            }
        }
        return false;
    }

    private void updateMantleMotion(float tpf) {
        mantleTimer += tpf;
        float progress = FastMath.clamp(mantleTimer / MANTLE_DURATION, 0f, 1f);
        float yCurve = FastMath.sin(progress * FastMath.HALF_PI);
        float xzCurve = progress * progress;

        float curX = FastMath.interpolateLinear(xzCurve, mantleStartPos.x, mantleTargetPos.x);
        float curY = FastMath.interpolateLinear(yCurve, mantleStartPos.y, mantleTargetPos.y);
        float curZ = FastMath.interpolateLinear(xzCurve, mantleStartPos.z, mantleTargetPos.z);

        this.warp(eyePos.set(curX, curY, curZ));
        lowerBodyNode.setLocalTranslation(curX, curY, curZ);

        float dip = FastMath.sin(progress * FastMath.PI) * 0.14f;
        cam.setLocation(eyePos.set(curX, curY + 1.62f - dip, curZ));
        currentGear.updateViewModel(tpf, false, false, true, progress);

        if (progress >= 1.0f) isMantling = false;
    }

    private void checkGroundSlope() {
        rayResults.clear();
        probeRay.setOrigin(eyePos.set(spatial.getLocalTranslation()).addLocal(0, 0.6f, 0));
        probeRay.setDirection(Vector3f.UNIT_Y.negate());
        terrainNode.collideWith(probeRay, rayResults);
        if (rayResults.size() > 0) {
            CollisionResult closest = rayResults.getClosestCollision();
            if (closest.getDistance() <= 1.8f) {
                groundNormal.set(closest.getContactNormal()).normalizeLocal();
                float dot = groundNormal.dot(Vector3f.UNIT_Y);
                currentSlopeDeg = FastMath.acos(FastMath.clamp(dot, -1f, 1f)) * FastMath.RAD_TO_DEG;
                return;
            }
        }
        groundNormal.set(0, 1, 0);
        currentSlopeDeg = 0.0f;
    }

    private void checkWallClimbing() {
        if (!actionHold || !currentGear.canAnchorToSteepWall() || vitals.getStamina() <= 1.0f) {
            isClimbingWall = false;
            return;
        }
        Vector3f playerPos = spatial.getLocalTranslation();
        bodyYawRotation.getRotationColumn(2, camDir).normalizeLocal();

        for (float h : new float[]{1.5f, 0.7f}) {
            rayResults.clear();
            probeRay.setOrigin(eyePos.set(playerPos.x, playerPos.y + h, playerPos.z));
            probeRay.setDirection(camDir);
            terrainNode.collideWith(probeRay, rayResults);
            if (rayResults.size() > 0 && rayResults.getClosestCollision().getDistance() <= 2.6f) {
                isClimbingWall = true;
                return;
            }
        }
        isClimbingWall = false;
    }

    public void setInput(String binding, boolean isPressed) {
        switch (binding) {
            case "Forward" -> forward = isPressed;
            case "Backward" -> backward = isPressed;
            case "Left" -> left = isPressed;
            case "Right" -> right = isPressed;
            case "Sprint" -> sprint = isPressed;
            case "Jump" -> {
                if (isPressed && !isClimbingWall && !isMantling) {
                    if (!tryStartMantle()) jump();
                }
            }
            case "UseGear" -> {
                actionHold = isPressed;
                if (isPressed) {
                    if (currentGear instanceof EnergyBarGear) {
                        if (vitals.useEnergyBar()) {
                            currentGear.onPrimaryAction();
                            showNotice("에너지바 섭취 (+60% 스태미나)");
                        } else {
                            showNotice("에너지바가 없습니다");
                        }
                    } else if (currentGear instanceof WaterBottleGear) {
                        if (vitals.useWaterBottle()) {
                            currentGear.onPrimaryAction();
                            showNotice("수분 섭취 (+70% 수분)");
                        } else {
                            showNotice("남은 물이 없습니다");
                        }
                    } else {
                        currentGear.onPrimaryAction();
                    }
                }
            }
        }
    }

    public void resetLookToNorth() {
        this.yaw = FastMath.PI;
        this.pitch = -0.12f;
        lookRotation.fromAngles(pitch, yaw, 0f);
        cam.setRotation(lookRotation);
    }

    public void setCurrentGear(ClimbingGear gear) { this.currentGear = gear; }
    public void setWeatherTempOffset(float offset) { this.weatherTempOffset = offset; }
    public float getCurrentSlopeDeg() { return currentSlopeDeg; }
    public float getYaw() { return yaw; }
    public boolean isClimbingWall() { return isClimbingWall; }
    public boolean isMantling() { return isMantling; }
    public boolean isSprinting() { return sprint && (forward || backward || left || right); }
    public String getStatusMessage() { return statusMessage; }
}