// EnergyBarGear.java
package com.everest.gear;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

public class EnergyBarGear extends ClimbingGear {

    public EnergyBarGear(AssetManager assetManager) {
        super("고열량 에너지바", assetManager);
    }

    @Override
    protected void buildProceduralMesh(AssetManager assetManager) {
        Material wrapperMat = makeMat(assetManager, new ColorRGBA(0.95f, 0.52f, 0.08f, 1.0f), 28f);
        Material silverTrimMat = makeMat(assetManager, new ColorRGBA(0.82f, 0.85f, 0.88f, 1.0f), 48f);
        Material chocoMat = makeMat(assetManager, new ColorRGBA(0.26f, 0.15f, 0.07f, 1.0f), 8f);
        Material gloveMat = makeMat(assetManager, new ColorRGBA(0.82f, 0.16f, 0.15f, 1.0f), 14f);
        Material sleeveMat = makeMat(assetManager, new ColorRGBA(0.90f, 0.52f, 0.12f, 1.0f), 10f);

        Geometry wrapper = new Geometry("Wrapper", new Box(0.030f, 0.052f, 0.015f));
        wrapper.setMaterial(wrapperMat);
        wrapper.setLocalTranslation(0, 0.04f, 0.02f);
        wrapper.rotate(0.15f, 0.25f, 0.1f);
        rightToolNode.attachChild(wrapper);

        Geometry foilRim = new Geometry("FoilRim", new Box(0.032f, 0.008f, 0.017f));
        foilRim.setMaterial(silverTrimMat);
        foilRim.setLocalTranslation(0, 0.075f, 0.022f);
        foilRim.rotate(0.15f, 0.25f, 0.1f);
        rightToolNode.attachChild(foilRim);

        Geometry choco = new Geometry("ChocolateBar", new Box(0.026f, 0.032f, 0.012f));
        choco.setMaterial(chocoMat);
        choco.setLocalTranslation(0, 0.095f, 0.025f);
        choco.rotate(0.15f, 0.25f, 0.1f);
        rightToolNode.attachChild(choco);

        Geometry rightHand = new Geometry("RightGlove", new Sphere(12, 12, 0.043f));
        rightHand.setMaterial(gloveMat);
        rightHand.setLocalTranslation(0, -0.01f, 0.01f);
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