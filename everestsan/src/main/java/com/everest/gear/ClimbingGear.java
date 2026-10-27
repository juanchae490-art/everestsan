package com.everest.gear;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

public abstract class ClimbingGear extends Node implements UsableGear {
    protected final String gearName;
    protected final Node rightToolNode = new Node("RightHandTool");
    protected final Node leftGloveNode = new Node("LeftHandGlove");

    protected float swingTimer = 0.0f;
    protected boolean isSwinging = false;
    private float walkCycle = 0.0f;

    private final Vector3f rightRestPos = new Vector3f(-0.23f, -0.20f, 0.40f);
    private final Vector3f leftRestPos = new Vector3f(0.23f, -0.19f, 0.37f);
    private final Quaternion rightRot = new Quaternion();
    private final Quaternion leftRot = new Quaternion();

    public ClimbingGear(String gearName, AssetManager assetManager) {
        super(gearName);
        this.gearName = gearName;
        buildLeftClimberGlove(assetManager);
        buildProceduralMesh(assetManager);

        rightToolNode.setLocalTranslation(rightRestPos);
        leftGloveNode.setLocalTranslation(leftRestPos);
        this.attachChild(rightToolNode);
        this.attachChild(leftGloveNode);
        this.setShadowMode(RenderQueue.ShadowMode.Off);
    }

    protected Material makeMat(AssetManager assetManager, ColorRGBA color, float shininess) {
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", color);
        mat.setColor("Ambient", color.mult(0.72f));
        mat.setColor("Specular", ColorRGBA.White.mult(0.35f));
        mat.setFloat("Shininess", shininess);
        return mat;
    }

    // 왼손 입체 방한 장갑 (어색한 회색 네모 박스 제거, 둥근 손가락 마디와 패딩 소매로 구성)
    private void buildLeftClimberGlove(AssetManager assetManager) {
        Material redGloveMat = makeMat(assetManager, new ColorRGBA(0.82f, 0.16f, 0.15f, 1.0f), 14f);
        Material darkLeatherMat = makeMat(assetManager, new ColorRGBA(0.16f, 0.17f, 0.19f, 1.0f), 20f);
        Material sleeveMat = makeMat(assetManager, new ColorRGBA(0.90f, 0.52f, 0.12f, 1.0f), 10f);
        Material cuffMat = makeMat(assetManager, new ColorRGBA(0.12f, 0.13f, 0.15f, 1.0f), 8f);

        // 고어텍스 오렌지 팔뚝 소매
        Geometry forearm = new Geometry("Forearm", new Cylinder(12, 12, 0.044f, 0.22f, true));
        forearm.setMaterial(sleeveMat);
        forearm.setLocalTranslation(0.04f, -0.10f, -0.08f);
        forearm.rotate(0.45f, -0.25f, 0.20f);
        leftGloveNode.attachChild(forearm);

        // 손목 방풍 커프스 벨크로
        Geometry wristCuff = new Geometry("WristCuff", new Cylinder(12, 12, 0.046f, 0.05f, true));
        wristCuff.setMaterial(cuffMat);
        wristCuff.setLocalTranslation(0.01f, -0.03f, -0.02f);
        wristCuff.rotate(0.45f, -0.25f, 0.20f);
        leftGloveNode.attachChild(wristCuff);

        // 손등 & 손바닥
        Geometry palm = new Geometry("Palm", new Sphere(12, 12, 0.044f));
        palm.setLocalScale(0.95f, 1.15f, 0.68f);
        palm.setMaterial(redGloveMat);
        leftGloveNode.attachChild(palm);

        // 자연스럽게 쥔 4개의 손가락 마디 (Sphere 캡슐 연결)
        for (int i = 0; i < 4; i++) {
            float fx = -0.024f + (i * 0.015f);
            Geometry finger = new Geometry("Finger_" + i, new Cylinder(8, 8, 0.009f, 0.042f, true));
            finger.setMaterial(i % 2 == 0 ? redGloveMat : darkLeatherMat);
            finger.setLocalTranslation(fx, 0.038f, 0.015f);
            finger.rotate(0.65f, 0f, 0f);
            leftGloveNode.attachChild(finger);
        }

        // 엄지손가락
        Geometry thumb = new Geometry("Thumb", new Cylinder(8, 8, 0.011f, 0.045f, true));
        thumb.setMaterial(redGloveMat);
        thumb.setLocalTranslation(-0.038f, 0.008f, 0.018f);
        thumb.rotate(0.3f, 0.4f, 0.7f);
        leftGloveNode.attachChild(thumb);
    }

    protected abstract void buildProceduralMesh(AssetManager assetManager);

    protected boolean isConsumableAnimation() {
        return false;
    }

    @Override
    public void onPrimaryAction() {
        if (!isSwinging) {
            isSwinging = true;
            swingTimer = 0.0f;
        }
    }

    public void updateViewModel(float tpf, boolean isMoving, boolean isSprinting, boolean isMantling, float mantleProgress) {
        if (isMantling) {
            // 암석 턱을 양손으로 짚고 끌어올리는 입체 회전 모션
            float push = FastMath.sin(mantleProgress * FastMath.PI);
            leftGloveNode.setLocalTranslation(0.15f, 0.08f - push * 0.30f, 0.42f);
            leftRot.fromAngles(0.6f * push, -0.2f, 0f);
            leftGloveNode.setLocalRotation(leftRot);

            rightToolNode.setLocalTranslation(-0.15f, 0.06f - push * 0.30f, 0.42f);
            rightRot.fromAngles(0.5f * push, 0.2f, 0f);
            rightToolNode.setLocalRotation(rightRot);
            return;
        }

        if (isMoving) {
            walkCycle += tpf * (isSprinting ? 12.5f : 7.2f);
        } else {
            walkCycle = FastMath.interpolateLinear(tpf * 5f, walkCycle, 0f);
        }

        float bobX = FastMath.cos(walkCycle) * (isSprinting ? 0.024f : 0.011f);
        float bobY = FastMath.abs(FastMath.sin(walkCycle)) * (isSprinting ? 0.030f : 0.014f);
        float swayPitch = FastMath.sin(walkCycle) * (isSprinting ? 0.14f : 0.06f);

        leftGloveNode.setLocalTranslation(leftRestPos.x + bobX, leftRestPos.y + bobY, leftRestPos.z);
        leftRot.fromAngles(swayPitch, 0f, -bobX * 1.5f);
        leftGloveNode.setLocalRotation(leftRot);

        if (isSwinging) {
            swingTimer += tpf * (isConsumableAnimation() ? 3.4f : 6.2f);
            if (swingTimer >= FastMath.PI) {
                isSwinging = false;
                rightToolNode.setLocalTranslation(rightRestPos);
                rightRot.fromAngles(0, 0, 0);
                rightToolNode.setLocalRotation(rightRot);
            } else {
                float p = swingTimer / FastMath.PI; // 0.0 -> 1.0 진행률
                if (isConsumableAnimation()) {
                    // [에너지바/물 섭취 모션] 입 쪽으로 들어 올린 뒤 두 번 기울여 마시는 모션
                    float lift = FastMath.sin(swingTimer);
                    float sipBob = FastMath.sin(p * FastMath.TWO_PI * 2.0f) * 0.08f * lift;
                    rightToolNode.setLocalTranslation(
                            FastMath.interpolateLinear(lift, rightRestPos.x, -0.04f),
                            FastMath.interpolateLinear(lift, rightRestPos.y, -0.05f + sipBob * 0.2f),
                            FastMath.interpolateLinear(lift, rightRestPos.z, 0.27f)
                    );
                    rightRot.fromAngles(-0.85f * lift + sipBob, 0.35f * lift, 0.25f * lift);
                    rightToolNode.setLocalRotation(rightRot);
                } else {
                    // [피켈 타격 모션] 1단계: 어깨 위로 백스윙(Wind-up) -> 2단계: 강하게 내리찍기(Strike) -> 3단계: 회수
                    float x, y, z, pitch, yaw, roll;
                    if (p < 0.30f) {
                        float w = p / 0.30f;
                        x = FastMath.interpolateLinear(w, rightRestPos.x, -0.26f);
                        y = FastMath.interpolateLinear(w, rightRestPos.y, -0.04f);
                        z = FastMath.interpolateLinear(w, rightRestPos.z, 0.32f);
                        pitch = -0.55f * w; // 뒤로 젖힘
                        yaw = -0.15f * w;
                        roll = -0.20f * w;
                    } else if (p < 0.62f) {
                        float w = (p - 0.30f) / 0.32f;
                        float snap = FastMath.sin(w * FastMath.HALF_PI);
                        x = FastMath.interpolateLinear(snap, -0.26f, -0.08f);
                        y = FastMath.interpolateLinear(snap, -0.04f, -0.12f);
                        z = FastMath.interpolateLinear(snap, 0.32f, 0.54f);
                        pitch = FastMath.interpolateLinear(snap, -0.55f, 0.78f); // 앞으로 강하게 찍음!
                        yaw = FastMath.interpolateLinear(snap, -0.15f, 0.22f);
                        roll = FastMath.interpolateLinear(snap, -0.20f, 0.15f);
                    } else {
                        float w = (p - 0.62f) / 0.38f;
                        x = FastMath.interpolateLinear(w, -0.08f, rightRestPos.x);
                        y = FastMath.interpolateLinear(w, -0.12f, rightRestPos.y);
                        z = FastMath.interpolateLinear(w, 0.54f, rightRestPos.z);
                        pitch = FastMath.interpolateLinear(w, 0.78f, 0f);
                        yaw = FastMath.interpolateLinear(w, 0.22f, 0f);
                        roll = FastMath.interpolateLinear(w, 0.15f, 0f);
                    }
                    rightToolNode.setLocalTranslation(x, y, z);
                    rightRot.fromAngles(pitch, yaw, roll);
                    rightToolNode.setLocalRotation(rightRot);
                }
            }
        } else {
            rightToolNode.setLocalTranslation(rightRestPos.x - bobX, rightRestPos.y + bobY, rightRestPos.z);
            rightRot.fromAngles(-swayPitch * 0.8f, 0f, bobX * 1.5f);
            rightToolNode.setLocalRotation(rightRot);
        }
    }

    public String getGearName() { return gearName; }
}