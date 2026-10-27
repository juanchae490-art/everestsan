// WaterBottleGear.java
package com.everest.gear;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

public class WaterBottleGear extends ClimbingGear {

    public WaterBottleGear(AssetManager assetManager) {
        super("보온 수통 (물)", assetManager);
    }

    @Override
    protected void buildProceduralMesh(AssetManager assetManager) {
        Material bottleMat = makeMat(assetManager, new ColorRGBA(0.14f, 0.55f, 0.86f, 1.0f), 52f);
        Material steelRingMat = makeMat(assetManager, new ColorRGBA(0.78f, 0.82f, 0.86f, 1.0f), 64f);
        Material capMat = makeMat(assetManager, new ColorRGBA(0.16f, 0.17f, 0.19f, 1.0f), 18f);
        Material gloveMat = makeMat(assetManager, new ColorRGBA(0.82f, 0.16f, 0.15f, 1.0f), 14f);
        Material sleeveMat = makeMat(assetManager, new ColorRGBA(0.90f, 0.52f, 0.12f, 1.0f), 10f);

        Geometry body = new Geometry("BottleBody", new Cylinder(16, 16, 0.035f, 0.15f, true));
        body.setMaterial(bottleMat);
        body.setLocalTranslation(0, 0.045f, 0.02f);
        body.rotate(FastMath.HALF_PI, 0, 0);
        rightToolNode.attachChild(body);

        Geometry neckRing = new Geometry("NeckRing", new Cylinder(16, 16, 0.030f, 0.02f, true));
        neckRing.setMaterial(steelRingMat);
        neckRing.setLocalTranslation(0, 0.125f, 0.02f);
        neckRing.rotate(FastMath.HALF_PI, 0, 0);
        rightToolNode.attachChild(neckRing);

        Geometry cap = new Geometry("BottleCap", new Cylinder(16, 16, 0.025f, 0.035f, true));
        cap.setMaterial(capMat);
        cap.setLocalTranslation(0, 0.148f, 0.02f);
        cap.rotate(FastMath.HALF_PI, 0, 0);
        rightToolNode.attachChild(cap);

        Geometry rightHand = new Geometry("RightGlove", new Sphere(12, 12, 0.043f));
        rightHand.setMaterial(gloveMat);
        rightHand.setLocalTranslation(0, 0.005f, 0.0f);
        rightToolNode.attachChild(rightHand);

        Geometry rightForearm = new Geometry("RightForearm", new Cylinder(12, 12, 0.042f, 0.20f, true));
        rightForearm.setMaterial(sleeveMat);
        rightForearm.setLocalTranslation(-0.03f, -0.10f, -0.08f);
        rightForearm.rotate(0.45f, 0.20f, -0.15f);
        rightToolNode.attachChild(rightForearm);
    }

    @Override
    protected boolean isConsumableAnimation() { return true; }

    @Override
    public boolean canAnchorToSteepWall() { return false; }

    @Override
    public float getStaminaConsumptionRate() { return 0.9f; }
}