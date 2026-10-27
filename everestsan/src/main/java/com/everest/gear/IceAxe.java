package com.everest.gear;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

public class IceAxe extends ClimbingGear {

    public IceAxe(AssetManager assetManager) {
        super("아이스 액스 (피켈)", assetManager);
    }

    @Override
    protected void buildProceduralMesh(AssetManager assetManager) {
        Material shaftMat = makeMat(assetManager, new ColorRGBA(0.14f, 0.16f, 0.18f, 1.0f), 36f);
        Material yellowGripMat = makeMat(assetManager, new ColorRGBA(0.95f, 0.72f, 0.08f, 1.0f), 18f);
        Material steelMat = makeMat(assetManager, new ColorRGBA(0.78f, 0.84f, 0.92f, 1.0f), 80f);
        Material gloveMat = makeMat(assetManager, new ColorRGBA(0.82f, 0.16f, 0.15f, 1.0f), 14f);
        Material sleeveMat = makeMat(assetManager, new ColorRGBA(0.90f, 0.52f, 0.12f, 1.0f), 10f);

        // 알루미늄 합금 메인 샤프트
        Geometry shaft = new Geometry("Shaft", new Cylinder(14, 14, 0.013f, 0.40f, true));
        shaft.setMaterial(shaftMat);
        shaft.rotate(FastMath.HALF_PI * 0.90f, -0.10f, 0);
        rightToolNode.attachChild(shaft);

        // 하단 미끄럼 방지 옐로우 텍스처 그립
        Geometry gripBand = new Geometry("GripBand", new Cylinder(14, 14, 0.0155f, 0.15f, true));
        gripBand.setMaterial(yellowGripMat);
        gripBand.setLocalTranslation(0, -0.06f, -0.01f);
        gripBand.rotate(FastMath.HALF_PI * 0.90f, -0.10f, 0);
        rightToolNode.attachChild(gripBand);

        // 상단 헤드 브라켓
        Geometry headCollar = new Geometry("HeadCollar", new Box(0.016f, 0.022f, 0.032f));
        headCollar.setMaterial(steelMat);
        headCollar.setLocalTranslation(0.01f, 0.18f, 0.02f);
        rightToolNode.attachChild(headCollar);

        // 전방 곡선형 아이스 피크 날 (2단 커브)
        Geometry pickBase = new Geometry("PickBase", new Box(0.006f, 0.016f, 0.075f));
        pickBase.setMaterial(steelMat);
        pickBase.setLocalTranslation(0.01f, 0.185f, 0.07f);
        pickBase.rotate(0.22f, 0, 0);
        rightToolNode.attachChild(pickBase);

        Geometry pickTip = new Geometry("PickTip", new Box(0.004f, 0.011f, 0.055f));
        pickTip.setMaterial(steelMat);
        pickTip.setLocalTranslation(0.01f, 0.162f, 0.125f);
        pickTip.rotate(0.48f, 0, 0);
        rightToolNode.attachChild(pickTip);

        // 후방 얼음 깎기 날 (Adze)
        Geometry backAdze = new Geometry("BackAdze", new Box(0.024f, 0.012f, 0.055f));
        backAdze.setMaterial(steelMat);
        backAdze.setLocalTranslation(0.01f, 0.175f, -0.035f);
        backAdze.rotate(-0.18f, 0, 0);
        rightToolNode.attachChild(backAdze);

        // 오른손 장갑 및 팔뚝 소매
        Geometry rightHand = new Geometry("RightGlove", new Sphere(12, 12, 0.043f));
        rightHand.setMaterial(gloveMat);
        rightHand.setLocalTranslation(0, -0.04f, 0.01f);
        rightToolNode.attachChild(rightHand);

        Geometry rightForearm = new Geometry("RightForearm", new Cylinder(12, 12, 0.042f, 0.20f, true));
        rightForearm.setMaterial(sleeveMat);
        rightForearm.setLocalTranslation(-0.03f, -0.12f, -0.08f);
        rightForearm.rotate(0.45f, 0.20f, -0.15f);
        rightToolNode.attachChild(rightForearm);
    }

    @Override
    public boolean canAnchorToSteepWall() { return true; }

    @Override
    public float getStaminaConsumptionRate() { return 1.0f; }
}