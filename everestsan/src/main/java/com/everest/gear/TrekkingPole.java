package com.everest.gear;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;

public class TrekkingPole extends ClimbingGear {

    public TrekkingPole(AssetManager assetManager) {
        super("Trekking Pole (Stamina Save)", assetManager);
    }

    @Override
    protected void buildProceduralMesh(AssetManager assetManager) {
        Material poleMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        poleMat.setBoolean("UseMaterialColors", true);
        poleMat.setColor("Diffuse", new ColorRGBA(0.2f, 0.65f, 0.9f, 1.0f));
        poleMat.setColor("Ambient", ColorRGBA.Gray);

        Material gloveMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        gloveMat.setBoolean("UseMaterialColors", true);
        gloveMat.setColor("Diffuse", new ColorRGBA(0.85f, 0.15f, 0.12f, 1.0f));
        gloveMat.setColor("Ambient", new ColorRGBA(0.5f, 0.1f, 0.1f, 1.0f));

        Geometry pole = new Geometry("Pole", new Cylinder(12, 12, 0.015f, 0.75f, true));
        pole.setMaterial(poleMat);
        pole.rotate(FastMath.HALF_PI * 0.85f, 0, 0);
        rightToolNode.attachChild(pole);

        Geometry rightHand = new Geometry("RightGlove", new Box(0.048f, 0.055f, 0.048f));
        rightHand.setMaterial(gloveMat);
        rightHand.setLocalTranslation(0, -0.04f, 0.02f);
        rightToolNode.attachChild(rightHand);
    }

    @Override
    public boolean canAnchorToSteepWall() { return false; }

    @Override
    public float getStaminaConsumptionRate() { return 0.65f; }
}