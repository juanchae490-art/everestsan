package com.everest;

import com.everest.controller.FirstPersonClimberControl;
import com.everest.gear.ClimbingGear;
import com.everest.gear.EnergyBarGear;
import com.everest.gear.IceAxe;
import com.everest.gear.WaterBottleGear;
import com.everest.model.PlayerVitals;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.FogFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.system.AppSettings;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class MainGameApp extends SimpleApplication implements ActionListener, AnalogListener {

    private enum WeatherType {
        CLEAR("쾌청", 0.07f, 1600f, 0.0f),
        SNOWING("강설", 0.28f, 520f, -5.0f),
        BLIZZARD("폭풍설", 0.68f, 195f, -15.0f);

        final String label;
        final float targetFogDensity;
        final float targetFogDistance;
        final float tempOffset;

        WeatherType(String label, float density, float distance, float tempOffset) {
            this.label = label;
            this.targetFogDensity = density;
            this.targetFogDistance = distance;
            this.tempOffset = tempOffset;
        }
    }

    private enum DetailLayer {
        CRISP_SNOW, ALPINE_GRANITE, GLACIAL_ICE, TENT_FABRIC
    }

    private PlayerVitals vitals;
    private FirstPersonClimberControl climberControl;
    private Node playerNode;
    private TerrainQuad terrain;

    private ClimbingGear iceAxe;
    private ClimbingGear energyBar;
    private ClimbingGear waterBottle;
    private ClimbingGear activeGear;
    private int activeSlot = 1;
    private Node viewModelNode;

    // 양방향 조명 (주 태양광 + 설원 하늘 반사광으로 어두운 그림자 해소)
    private DirectionalLight sunLight;
    private DirectionalLight skyBounceLight;
    private AmbientLight ambientLight;
    private FogFilter fogFilter;
    private ParticleEmitter snowEmitter;

    private final Vector3f[] supplyDepotPositions = new Vector3f[3];
    private final Vector3f summitPosition = new Vector3f();

    private float gameTimeHours = 10.0f; // 가장 화사하고 밝은 오전 10시 시작
    private int dayCount = 1;
    private static final float SECONDS_PER_GAME_HOUR = 50.0f;

    private WeatherType currentWeather = WeatherType.CLEAR;
    private float weatherTimer = 0.0f;
    private float nextWeatherDuration = 45.0f;
    private float currentFogDensity = 0.07f;
    private float currentFogDist = 1600f;

    private BufferedImage hudBuffer;
    private Graphics2D hudG2d;
    private Texture2D hudTexture;
    private final AWTLoader awtLoader = new AWTLoader();
    private float hudRefreshTimer = 0.0f;

    private final ColorRGBA skyColor = new ColorRGBA();

    public static void main(String[] args) {
        MainGameApp app = new MainGameApp();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("CAIRN : EVEREST 8848");
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setFrameRate(60);
        settings.setSamples(4);
        settings.setVSync(false);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        setDisplayStatView(false);
        setDisplayFps(false);

        FlyCamAppState flyCamState = stateManager.getState(FlyCamAppState.class);
        if (flyCamState != null) {
            stateManager.detach(flyCamState);
        }
        if (flyCam != null) {
            flyCam.setEnabled(false);
        }
        inputManager.setCursorVisible(false);

        float aspect = (float) settings.getWidth() / settings.getHeight();
        cam.setFrustumPerspective(65.0f, aspect, 0.05f, 2600.0f);

        BulletAppState bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        bulletAppState.getPhysicsSpace().setAccuracy(1.0f / 60.0f);

        setupBrightAlpineLighting();
        setupVolumetricSnowstorm();

        Node worldNode = buildNaturalAlpineMassifWorld(bulletAppState);
        rootNode.attachChild(worldNode);

        vitals = new PlayerVitals();
        iceAxe = new IceAxe(assetManager);
        energyBar = new EnergyBarGear(assetManager);
        waterBottle = new WaterBottleGear(assetManager);
        activeGear = iceAxe;

        viewModelNode = new Node("ViewModelNode");
        rootNode.attachChild(viewModelNode);
        viewModelNode.attachChild(activeGear);

        playerNode = new Node("Player");
        climberControl = new FirstPersonClimberControl(cam, worldNode, rootNode, assetManager, vitals, activeGear);
        playerNode.addControl(climberControl);
        rootNode.attachChild(playerNode);
        bulletAppState.getPhysicsSpace().add(climberControl);

        warpPlayerToCamp();

        setupMinimalistIndieHud();
        setupInputs();
    }

    private float getExactGroundY(float worldX, float worldZ) {
        if (terrain != null) {
            float h = terrain.getHeight(new Vector2f(worldX, worldZ));
            if (!Float.isNaN(h)) {
                return h;
            }
        }
        return calculateNaturalAlpineHeight(worldX, worldZ);
    }

    private void warpPlayerToCamp() {
        float startX = 0f;
        float startZ = 310f;
        float startY = getExactGroundY(startX, startZ) + 2.0f;
        playerNode.setLocalTranslation(startX, startY, startZ);
        if (climberControl != null) {
            climberControl.warp(new Vector3f(startX, startY, startZ));
            climberControl.resetLookToNorth();
            climberControl.showNotice("베이스캠프 (5,000m) — 붉은 표지 폴대를 따라 자연 능선을 등반하세요");
        }
        if (vitals != null) {
            vitals.resetToCamp();
        }
    }

    private double hashNoise(int x, int y, int seed) {
        int n = x * 374761393 + y * 668265263 + seed * 1442695041;
        n = (n ^ (n >> 13)) * 1274126177;
        return ((n ^ (n >> 16)) & 0x7fffffff) / (double) 0x7fffffff;
    }

    private double smoothNoise(double x, double y, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fy * fy * (3.0 - 2.0 * fy);

        double n00 = hashNoise(ix, iy, seed);
        double n10 = hashNoise(ix + 1, iy, seed);
        double n01 = hashNoise(ix, iy + 1, seed);
        double n11 = hashNoise(ix + 1, iy + 1, seed);

        return (n00 * (1 - u) + n10 * u) * (1 - v) + (n01 * (1 - u) + n11 * u) * v;
    }

    private double fbmNoise(double x, double y, int octaves, int seed) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 0.5;
        for (int i = 0; i < octaves; i++) {
            total += smoothNoise(x * frequency, y * frequency, seed + i * 37) * amplitude;
            frequency *= 2.02;
            amplitude *= 0.5;
        }
        return total;
    }

    private double ridgedNoise(double x, double y, int octaves, int seed) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 0.5;
        for (int i = 0; i < octaves; i++) {
            double n = smoothNoise(x * frequency, y * frequency, seed + i * 53);
            double ridge = 1.0 - Math.abs(n * 2.0 - 1.0);
            total += (ridge * ridge) * amplitude;
            frequency *= 2.05;
            amplitude *= 0.48;
        }
        return total;
    }

    // 산의 자연스러운 주 능선을 따라 이어지는 등산 루트 X좌표
    private float getClimbingRouteX(float z) {
        return FastMath.sin((310f - z) * 0.011f) * 38.0f;
    }

    // [인위적인 성벽 제거 & 도메인 워핑 적용] 유기적인 곡선 능선과 골짜기, 자연 단차를 가진 고산 지형 방정식
    private float calculateNaturalAlpineHeight(float x, float z) {
        // 1. 도메인 워핑(Domain Warping): 직선이나 원뿔 모양을 없애고 실제 산맥처럼 휘어지는 지질 구조 생성
        float warpX = x + (float) (fbmNoise(x * 0.0045, z * 0.0045, 3, 11) - 0.5) * 65.0f;
        float warpZ = z + (float) (fbmNoise(x * 0.0045, z * 0.0045, 3, 22) - 0.5) * 65.0f;

        // 2. 남쪽 평원(z=+320)에서 북쪽 주봉(z=-215)으로 자연스럽게 솟아오르는 거대 산악 매시프
        float northProgress = FastMath.clamp((325f - z) / 550f, 0f, 1f);
        float broadSlope = FastMath.pow(northProgress, 1.42f) * 145.0f;

        // 주봉(Main Summit: X=0, Z=-215) + 서릉(X=-145, Z=-80) + 동릉(X=+145, Z=-70)이 부드러운 안부(Saddle)로 연결됨
        float dMain = FastMath.sqrt((warpX * warpX) / (210f * 210f) + ((warpZ + 215f) * (warpZ + 215f)) / (240f * 240f));
        float mainPeak = FastMath.pow(Math.max(0f, 1.0f - dMain), 1.7f) * 125.0f;

        float dWest = FastMath.sqrt(((warpX + 145f) * (warpX + 145f)) / (175f * 175f) + ((warpZ + 80f) * (warpZ + 80f)) / (210f * 210f));
        float westPeak = FastMath.pow(Math.max(0f, 1.0f - dWest), 1.8f) * 95.0f;

        float dEast = FastMath.sqrt(((warpX - 145f) * (warpX - 145f)) / (175f * 175f) + ((warpZ + 70f) * (warpZ + 70f)) / (210f * 210f));
        float eastPeak = FastMath.pow(Math.max(0f, 1.0f - dEast), 1.8f) * 102.0f;

        float massifCore = broadSlope + mainPeak + westPeak + eastPeak;

        // 3. 자연스러운 암릉(Ridged Multifractal)과 크고 작은 구릉 높낮이(Undulating Hills) 추가
        float altitudeFactor = FastMath.clamp(massifCore / 45.0f, 0.12f, 1.0f);
        float majorRidges = (float) ridgedNoise(warpX * 0.011, warpZ * 0.011, 4, 303) * 32.0f * altitudeFactor;
        float rollingHills = (float) (fbmNoise(x * 0.025, z * 0.025, 3, 404) - 0.45) * 11.0f;
        float naturalTerraces = FastMath.sin(massifCore * 0.14f + (float) smoothNoise(x * 0.04, z * 0.04, 505) * 2.0f) * 2.2f * altitudeFactor;

        float totalHeight = massifCore + majorRidges + rollingHills + naturalTerraces;

        // 4. 등산로 주변은 인위적인 벽이 생기지 않도록 거친 돌기(majorRidges)만 부드럽게 완화!
        float routeX = getClimbingRouteX(z);
        float distToRoute = Math.abs(x - routeX);
        if (distToRoute < 24.0f) {
            float smooth = 1.0f - (distToRoute / 24.0f);
            smooth = smooth * smooth * (3.0f - 2.0f * smooth);
            totalHeight -= majorRidges * 0.68f * smooth;
        }

        // 5. 베이스캠프(X=0, Z=310) 주변은 완만한 자연 설원 구릉으로 연결
        float campDist = FastMath.sqrt(x * x + (z - 310f) * (z - 310f));
        if (campDist < 60.0f) {
            float t = FastMath.clamp(campDist / 60.0f, 0f, 1f);
            float s = t * t * (3.0f - 2.0f * t);
            float gentleRoll = (float) smoothNoise(x * 0.04, z * 0.04, 606) * 1.8f;
            totalHeight = FastMath.interpolateLinear(s, 3.0f + gentleRoll, totalHeight);
        }

        return Math.max(0f, totalHeight);
    }

    // [발밑까지 선명한 512x512 고해상도 심리스 디테일 텍스처 생성]
    private Texture2D createDetailLayerTexture(DetailLayer layer) {
        int res = 512;
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < res; y++) {
            for (int x = 0; x < res; x++) {
                // 타일 경계선이 절대 보이지 않도록 삼각함수 주기 좌표 사용
                double u = Math.sin((x / (double) res) * Math.PI * 2.0) * 3.0;
                double v = Math.sin((y / (double) res) * Math.PI * 2.0) * 3.0;
                double n1 = fbmNoise(u + 5.0, v + 5.0, 4, layer.ordinal() * 111);
                double n2 = smoothNoise(x * 0.18, y * 0.18, layer.ordinal() * 222);

                int r = 240, g = 245, b = 252;

                switch (layer) {
                    case CRISP_SNOW -> {
                        // 밝고 화사한 순백색 알파인 설원 + 미세한 눈결 음영 (절대 검게 죽지 않음!)
                        double ripple = Math.sin((x / (double) res) * Math.PI * 6.0 + n1 * 4.0) * 6.0;
                        double sparkle = (n2 - 0.5) * 10.0;
                        r = (int) (240 + ripple + sparkle);
                        g = (int) (246 + ripple + sparkle);
                        b = (int) (254 + ripple * 0.6 + sparkle);
                    }
                    case ALPINE_GRANITE -> {
                        // 밝은 슬레이트 화강암 질감 + 암석 틈새에 낀 만년설 서리
                        double strata = Math.sin((y / (double) res) * Math.PI * 10.0 + n1 * 5.0) * 18.0;
                        double grain = (n2 - 0.5) * 28.0;
                        r = (int) (122 + strata + grain);
                        g = (int) (132 + strata + grain);
                        b = (int) (148 + strata + grain);
                        if (n1 > 0.62 && n2 > 0.55) {
                            // 암벽 미세 요철에 쌓인 하얀 눈
                            r = 228; g = 236; b = 246;
                        }
                    }
                    case GLACIAL_ICE -> {
                        // 투명감 있는 하늘빛-청백색 빙하 + 하얀 서리 결정 맥
                        double frost = Math.pow(Math.abs(n1 - 0.5) * 2.0, 0.4) * 38.0;
                        r = (int) (155 + frost + (n2 - 0.5) * 12);
                        g = (int) (208 + frost * 0.8 + (n2 - 0.5) * 12);
                        b = (int) (245 + frost * 0.4);
                    }
                    case TENT_FABRIC -> {
                        double weave = Math.sin(x * 0.5) * Math.cos(y * 0.5) * 6.0;
                        r = (int) (238 + weave);
                        g = (int) (165 + weave);
                        b = (int) (35 + weave);
                    }
                }

                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));
                img.setRGB(x, y, (255 << 24) | (r << 16) | (g << 8) | b);
            }
        }

        Texture2D tex = new Texture2D(awtLoader.load(img, true));
        tex.setWrap(Texture.WrapMode.Repeat);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        tex.setMinFilter(Texture.MinFilter.Trilinear);
        tex.setAnisotropicFilter(8);
        return tex;
    }

    // 지형의 경사각과 고도에 따라 설원(Red), 화강암(Green), 청빙하(Blue) 비율을 정밀하게 섞어주는 1024x1024 알파 스플랫 맵
    private Texture2D buildTerrainAlphaSplatMap(float worldHalfSize) {
        int res = 1024;
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        Vector3f normal = new Vector3f();

        for (int py = 0; py < res; py++) {
            float v = 1.0f - (py / (float) (res - 1));
            float wz = (v * 2.0f - 1.0f) * worldHalfSize;

            for (int px = 0; px < res; px++) {
                float u = px / (float) (res - 1);
                float wx = (u * 2.0f - 1.0f) * worldHalfSize;

                float eps = 2.0f;
                float hL = calculateNaturalAlpineHeight(wx - eps, wz);
                float hR = calculateNaturalAlpineHeight(wx + eps, wz);
                float hD = calculateNaturalAlpineHeight(wx, wz - eps);
                float hU = calculateNaturalAlpineHeight(wx, wz + eps);

                normal.set(hL - hR, 2.0f * eps, hD - hU).normalizeLocal();
                float slopeDeg = FastMath.acos(FastMath.clamp(normal.y, -1f, 1f)) * FastMath.RAD_TO_DEG;

                float noise = (float) (smoothNoise(wx * 0.08, wz * 0.08, 909) - 0.5) * 8.0f;
                float s = slopeDeg + noise;

                // Red = 설원(Snow), Green = 화강암(Rock), Blue = 빙하(Ice)
                float snowWeight = FastMath.clamp(1.0f - (s - 20.0f) / 14.0f, 0f, 1f);
                float rockWeight = FastMath.clamp((s - 27.0f) / 12.0f, 0f, 1f);
                float iceWeight = FastMath.clamp(1.0f - snowWeight - rockWeight, 0f, 1f);

                // 정규화하여 합이 항상 1.0이 되도록 보장 (어두운 얼룩 0%!)
                float sum = snowWeight + rockWeight + iceWeight + 0.0001f;
                int r = Math.min(255, Math.max(0, (int) ((snowWeight / sum) * 255f)));
                int g = Math.min(255, Math.max(0, (int) ((rockWeight / sum) * 255f)));
                int b = Math.min(255, Math.max(0, (int) ((iceWeight / sum) * 255f)));

                img.setRGB(px, py, (255 << 24) | (r << 16) | (g << 8) | b);
            }
        }

        Texture2D alphaTex = new Texture2D(awtLoader.load(img, true));
        alphaTex.setWrap(Texture.WrapMode.EdgeClamp);
        alphaTex.setMagFilter(Texture.MagFilter.Bilinear);
        alphaTex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        return alphaTex;
    }

    private void setupBrightAlpineLighting() {
        // 1. 주 태양광 (따뜻하고 밝은 고산 직사광)
        sunLight = new DirectionalLight();
        sunLight.setDirection(new Vector3f(0.45f, -0.78f, -0.42f).normalizeLocal());
        rootNode.addLight(sunLight);

        // 2. 보조 하늘/설원 반사광 (그늘진 북사면이 검게 죽지 않고 화사한 청백색으로 보이게 함)
        skyBounceLight = new DirectionalLight();
        skyBounceLight.setDirection(new Vector3f(-0.45f, -0.55f, 0.55f).normalizeLocal());
        rootNode.addLight(skyBounceLight);

        // 3. 밝은 환경광
        ambientLight = new AmbientLight();
        rootNode.addLight(ambientLight);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);

        try {
            DirectionalLightShadowFilter dlsf = new DirectionalLightShadowFilter(assetManager, 2048, 3);
            dlsf.setLight(sunLight);
            dlsf.setShadowIntensity(0.24f); // 그림자가 너무 시커멓지 않고 은은하게 지도록 조정
            fpp.addFilter(dlsf);
        } catch (Exception ignored) {}

        fogFilter = new FogFilter();
        fogFilter.setFogColor(new ColorRGBA(0.76f, 0.85f, 0.94f, 1.0f));
        fogFilter.setFogDensity(currentFogDensity);
        fogFilter.setFogDistance(currentFogDist);
        fpp.addFilter(fogFilter);

        viewPort.addProcessor(fpp);
    }

    private void setupVolumetricSnowstorm() {
        int sRes = 64;
        BufferedImage flakeImg = new BufferedImage(sRes, sRes, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sRes; y++) {
            for (int x = 0; x < sRes; x++) {
                float dx = (x - 31.5f) / 31.5f;
                float dy = (y - 31.5f) / 31.5f;
                float dist = FastMath.sqrt(dx * dx + dy * dy);
                int alpha = (int) (FastMath.clamp(1.0f - dist, 0f, 1f) * 230f);
                flakeImg.setRGB(x, y, (alpha << 24) | (245 << 16) | (250 << 8) | 255);
            }
        }
        Texture2D flakeTex = new Texture2D(awtLoader.load(flakeImg, true));

        snowEmitter = new ParticleEmitter("WideAreaBlizzard", ParticleMesh.Type.Triangle, 2800);
        Material snowMat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        snowMat.setTexture("Texture", flakeTex);
        snowMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        snowEmitter.setMaterial(snowMat);
        snowEmitter.setImagesX(1);
        snowEmitter.setImagesY(1);

        snowEmitter.setShape(new EmitterBoxShape(new Vector3f(-45f, -4f, -45f), new Vector3f(45f, 24f, 45f)));
        snowEmitter.setStartColor(new ColorRGBA(0.96f, 0.98f, 1.0f, 0.85f));
        snowEmitter.setEndColor(new ColorRGBA(0.90f, 0.95f, 1.0f, 0.15f));
        snowEmitter.setStartSize(0.13f);
        snowEmitter.setEndSize(0.07f);
        snowEmitter.setGravity(0, -2.5f, 0);
        snowEmitter.setLowLife(2.2f);
        snowEmitter.setHighLife(4.2f);
        snowEmitter.getParticleInfluencer().setInitialVelocity(new Vector3f(-9f, -6f, 5f));
        snowEmitter.getParticleInfluencer().setVelocityVariation(0.45f);
        snowEmitter.setParticlesPerSec(0);
        rootNode.attachChild(snowEmitter);
    }

    private Node buildNaturalAlpineMassifWorld(BulletAppState bullet) {
        Node world = new Node("NaturalAlpineWorld");

        int size = 257;
        float[] heightmap = new float[size * size];
        float scaleXZ = 3.5f;
        int half = size / 2;
        float worldHalfSize = half * scaleXZ;

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                float worldX = (x - half) * scaleXZ;
                float worldZ = (z - half) * scaleXZ;
                heightmap[z * size + x] = calculateNaturalAlpineHeight(worldX, worldZ);
            }
        }

        terrain = new TerrainQuad("AlpineMassifTerrain", 65, size, heightmap);
        terrain.setLocalScale(scaleXZ, 1.0f, scaleXZ);

        // [3중 멀티 텍스처 스플래팅] 멀리서도 자연스럽고 발밑을 내려다봐도 110배 고밀도 눈 결정/암석 질감이 선명히 보임!
        Material terrainMat = new Material(assetManager, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        terrainMat.setTexture("AlphaMap", buildTerrainAlphaSplatMap(worldHalfSize));
        terrainMat.setTexture("DiffuseMap", createDetailLayerTexture(DetailLayer.CRISP_SNOW));
        terrainMat.setFloat("DiffuseMap_0_scale", 110.0f);
        terrainMat.setTexture("DiffuseMap_1", createDetailLayerTexture(DetailLayer.ALPINE_GRANITE));
        terrainMat.setFloat("DiffuseMap_1_scale", 75.0f);
        terrainMat.setTexture("DiffuseMap_2", createDetailLayerTexture(DetailLayer.GLACIAL_ICE));
        terrainMat.setFloat("DiffuseMap_2_scale", 80.0f);
        terrainMat.setColor("Diffuse", ColorRGBA.White);
        terrainMat.setColor("Ambient", new ColorRGBA(0.85f, 0.90f, 0.96f, 1.0f));
        terrainMat.setFloat("Shininess", 16.0f);

        terrain.setMaterial(terrainMat);
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        world.attachChild(terrain);
        terrain.updateGeometricState();

        buildExpeditionCamps(world);
        buildOrganicAlpineOutcropsAndRoute(world);

        RigidBodyControl worldPhysics = new RigidBodyControl(0.0f);
        world.addControl(worldPhysics);
        bullet.getPhysicsSpace().add(worldPhysics);

        return world;
    }

    private Material createLitDetailMaterial(DetailLayer layer, Color tint, float shininess) {
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setTexture("DiffuseMap", createDetailLayerTexture(layer));
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", new ColorRGBA(tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f, 1f));
        mat.setColor("Ambient", new ColorRGBA(0.75f, 0.80f, 0.88f, 1.0f));
        mat.setColor("Specular", ColorRGBA.White.mult(0.22f));
        mat.setFloat("Shininess", shininess);
        return mat;
    }

    // [사각형 마인크래프트 Box 완전 배제!] 둥근 다면체 자연석 클러스터와 빙하 지형물만 유기적으로 배치
    private Node createNaturalRockCluster(String name, Material rockMat, Material snowMat, float radius) {
        Node cluster = new Node(name);

        // 주 자연 암석 (비대칭 타원형 다면체)
        Sphere mainMesh = new Sphere(10, 12, radius);
        mainMesh.scaleTextureCoordinates(new Vector2f(2f, 2f));
        Geometry mainRock = new Geometry(name + "_Main", mainMesh);
        mainRock.setLocalScale(1.45f, 0.82f, 1.15f);
        mainRock.setMaterial(rockMat);
        cluster.attachChild(mainRock);

        // 옆에 자연스럽게 맞물린 보조 바위
        Sphere subMesh = new Sphere(9, 10, radius * 0.72f);
        subMesh.scaleTextureCoordinates(new Vector2f(2f, 2f));
        Geometry subRock = new Geometry(name + "_Sub", subMesh);
        subRock.setLocalScale(1.3f, 0.75f, 1.1f);
        subRock.setMaterial(rockMat);
        subRock.setLocalTranslation(radius * 0.75f, -radius * 0.15f, radius * 0.35f);
        subRock.rotate(0.2f, 0.5f, -0.15f);
        cluster.attachChild(subRock);

        // 바위 윗면에 소복하게 쌓인 자연 설원 캡
        Sphere snowMesh = new Sphere(10, 12, radius * 0.92f);
        Geometry snowDrift = new Geometry(name + "_Snow", snowMesh);
        snowDrift.setLocalScale(1.38f, 0.35f, 1.08f);
        snowDrift.setMaterial(snowMat);
        snowDrift.setLocalTranslation(0f, radius * 0.55f, 0f);
        cluster.attachChild(snowDrift);

        cluster.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return cluster;
    }

    private void buildOrganicAlpineOutcropsAndRoute(Node world) {
        Material graniteMat = createLitDetailMaterial(DetailLayer.ALPINE_GRANITE, new Color(210, 218, 228), 12f);
        Material snowMat = createLitDetailMaterial(DetailLayer.CRISP_SNOW, new Color(250, 252, 255), 18f);
        Material glacierMat = createLitDetailMaterial(DetailLayer.GLACIAL_ICE, new Color(200, 235, 255), 48f);

        Material redFlagMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        redFlagMat.setBoolean("UseMaterialColors", true);
        redFlagMat.setColor("Diffuse", new ColorRGBA(0.92f, 0.22f, 0.18f, 1.0f));
        redFlagMat.setColor("Ambient", new ColorRGBA(0.65f, 0.15f, 0.12f, 1.0f));

        // 1. 산 사면 곳곳에 자연스럽게 파묻힌 둥근 화강암 암석 군락 & 빙하 노출부 65개 (사각형 Box 0개!)
        for (int i = 0; i < 65; i++) {
            float rz = 250f - (i * 6.8f);
            float routeX = getClimbingRouteX(rz);
            float side = (i % 2 == 0) ? 1f : -1f;
            float offset = 18.0f + FastMath.abs(FastMath.sin(i * 19.7f)) * 110.0f;
            float rx = routeX + side * offset;
            float ry = getExactGroundY(rx, rz);

            float rad = 2.2f + (i % 5) * 1.3f;
            Material mat = (i % 4 == 0) ? glacierMat : graniteMat;
            Node outcrop = createNaturalRockCluster("NaturalOutcrop_" + i, mat, snowMat, rad);
            outcrop.setLocalTranslation(rx, ry - rad * 0.25f, rz);
            outcrop.rotate(0.08f * (i % 3), i * 0.9f, 0.06f * (i % 2));
            world.attachChild(outcrop);
        }

        // 2. 등산로를 따라 자연 돌무더기(Cairn)와 붉은 대나무 표지 폴대 배치
        for (float z = 285f; z >= -195f; z -= 24f) {
            float mx = getClimbingRouteX(z) + 5.5f;
            float my = getExactGroundY(mx, z);

            // 둥근 조약돌 3개를 쌓아 만든 진짜 돌탑(Cairn)
            for (int s = 0; s < 3; s++) {
                float r = 0.42f - s * 0.10f;
                Geometry stone = new Geometry("CairnStone_" + z + "_" + s, new Sphere(8, 8, r));
                stone.setLocalScale(1.2f, 0.65f, 1.1f);
                stone.setMaterial(graniteMat);
                stone.setLocalTranslation(mx, my + s * 0.28f + 0.1f, z);
                world.attachChild(stone);
            }

            Geometry pole = new Geometry("MarkerPole_" + z, new Cylinder(8, 8, 0.08f, 2.8f, true));
            pole.setMaterial(redFlagMat);
            pole.setLocalTranslation(mx, my + 1.4f, z);
            pole.rotate(FastMath.HALF_PI, 0, 0);
            world.attachChild(pole);
        }

        // 3. 최정상(X=0, Z=-215) 자연 암봉 크라운 & 정상 폴대
        float sx = 0f;
        float sz = -215f;
        float sy = getExactGroundY(sx, sz);
        summitPosition.set(sx, sy, sz);

        Node summitOutcrop = createNaturalRockCluster("SummitCrown", graniteMat, snowMat, 4.2f);
        summitOutcrop.setLocalTranslation(sx, sy - 1.2f, sz - 5.0f);
        world.attachChild(summitOutcrop);

        Geometry summitPole = new Geometry("SummitPole", new Cylinder(10, 10, 0.16f, 6.0f, true));
        summitPole.setMaterial(redFlagMat);
        summitPole.setLocalTranslation(sx, sy + 2.8f, sz);
        summitPole.rotate(FastMath.HALF_PI, 0, 0);
        world.attachChild(summitPole);
    }

    // 곡선형 지오데식 원정대 돔 텐트 조립
    private Node createGeodesicDomeTent(String name, Material fabricMat, Material darkMat, float radius) {
        Node dome = new Node(name);

        // 지면 아래로 묻히는 원형 방수 그라운드시트
        Geometry baseRing = new Geometry(name + "_Base", new Cylinder(16, 16, radius * 1.02f, 0.6f, true));
        baseRing.setMaterial(darkMat);
        baseRing.setLocalTranslation(0, -0.1f, 0);
        baseRing.rotate(FastMath.HALF_PI, 0, 0);
        dome.attachChild(baseRing);

        // 반구형 지오데식 돔 텐트 본체
        Sphere domeMesh = new Sphere(16, 18, radius);
        domeMesh.scaleTextureCoordinates(new Vector2f(2f, 2f));
        Geometry body = new Geometry(name + "_Body", domeMesh);
        body.setLocalScale(1.0f, 0.68f, 1.15f);
        body.setMaterial(fabricMat);
        body.setLocalTranslation(0, 0.15f, 0);
        dome.attachChild(body);

        // 전면 터널형 방풍 현관(Vestibule)
        Geometry vestibule = new Geometry(name + "_Vestibule", new Cylinder(12, 12, radius * 0.42f, radius * 0.8f, true));
        vestibule.setMaterial(fabricMat);
        vestibule.setLocalTranslation(0, radius * 0.25f, radius * 0.85f);
        dome.attachChild(vestibule);

        dome.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return dome;
    }

    private void buildExpeditionCamps(Node world) {
        Material yellowTentMat = createLitDetailMaterial(DetailLayer.TENT_FABRIC, new Color(250, 190, 40), 14f);
        Material orangeTentMat = createLitDetailMaterial(DetailLayer.TENT_FABRIC, new Color(240, 95, 35), 14f);
        Material darkMat = createLitDetailMaterial(DetailLayer.ALPINE_GRANITE, new Color(65, 72, 82), 16f);

        Material redPoleMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        redPoleMat.setBoolean("UseMaterialColors", true);
        redPoleMat.setColor("Diffuse", new ColorRGBA(0.92f, 0.22f, 0.18f, 1.0f));
        redPoleMat.setColor("Ambient", new ColorRGBA(0.65f, 0.15f, 0.12f, 1.0f));

        // 베이스캠프 지오데식 돔 텐트 5동
        float[][] campTents = {
                {-15f, 304f, 0.4f}, {15f, 305f, -0.4f},
                {-20f, 318f, 0.6f}, {19f, 319f, -0.5f},
                {-12f, 326f, 0.2f}
        };
        for (int i = 0; i < campTents.length; i++) {
            float tx = campTents[i][0];
            float tz = campTents[i][1];
            float ty = getExactGroundY(tx, tz);
            Node tent = createGeodesicDomeTent("BaseTent_" + i, (i % 2 == 0) ? yellowTentMat : orangeTentMat, darkMat, 2.8f);
            tent.setLocalTranslation(tx, ty, tz);
            tent.rotate(0, campTents[i][2], 0);
            world.attachChild(tent);
        }

        // 보급소 3곳 (네모 상자 대신 둥근 원통형 산소통/보급 배럴 드럼 배치!)
        float[][] depotXZ = {
                {7.0f, 298f},
                {getClimbingRouteX(75f) + 6.0f, 75f},
                {getClimbingRouteX(-85f) - 6.0f, -85f}
        };

        for (int d = 0; d < depotXZ.length; d++) {
            float dx = depotXZ[d][0];
            float dz = depotXZ[d][1];
            float dy = getExactGroundY(dx, dz);
            supplyDepotPositions[d] = new Vector3f(dx, dy + 1.0f, dz);

            if (d > 0) {
                float tx = dx + (d == 1 ? 4.8f : -4.8f);
                float ty = getExactGroundY(tx, dz);
                Node shelter = createGeodesicDomeTent("CampShelter_" + d, orangeTentMat, darkMat, 2.5f);
                shelter.setLocalTranslation(tx, ty, dz);
                world.attachChild(shelter);
            }

            // 원통형 보급 배럴 드럼 3개
            for (int c = 0; c < 3; c++) {
                float bx = dx + c * 1.1f;
                float by = getExactGroundY(bx, dz);
                Geometry barrel = new Geometry("SupplyBarrel_" + d + "_" + c, new Cylinder(14, 14, 0.42f, 1.15f, true));
                barrel.setMaterial(c % 2 == 0 ? orangeTentMat : yellowTentMat);
                barrel.setLocalTranslation(bx, by + 0.55f, dz);
                barrel.rotate(FastMath.HALF_PI, 0, 0);
                barrel.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                world.attachChild(barrel);
            }

            Geometry flagPole = new Geometry("DepotPole_" + d, new Cylinder(10, 10, 0.11f, 4.8f, true));
            flagPole.setMaterial(redPoleMat);
            flagPole.setLocalTranslation(dx - 1.2f, dy + 2.2f, dz);
            flagPole.rotate(FastMath.HALF_PI, 0, 0);
            world.attachChild(flagPole);
        }
    }

    private void setupMinimalistIndieHud() {
        guiNode.detachAllChildren();
        int w = settings.getWidth();
        int h = settings.getHeight();

        hudBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        hudG2d = hudBuffer.createGraphics();
        hudG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hudG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        hudTexture = new Texture2D();
        Quad fullScreenQuad = new Quad(w, h);
        Geometry hudGeom = new Geometry("MinimalistHud", fullScreenQuad);
        Material hudMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        hudMat.setTexture("ColorMap", hudTexture);
        hudMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        hudGeom.setMaterial(hudMat);
        guiNode.attachChild(hudGeom);

        refreshMinimalistHud();
    }

    private void drawShadowedText(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(new Color(0, 0, 0, 165));
        g.drawString(text, x + 1, y + 1);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawMinimalBar(Graphics2D g, int x, int y, int width, float pct, String label, Color fillCol) {
        g.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        drawShadowedText(g, label, x, y - 5, new Color(235, 242, 250));

        String valStr = String.format("%.0f%%", pct);
        int vw = g.getFontMetrics().stringWidth(valStr);
        drawShadowedText(g, valStr, x + width - vw, y - 5, new Color(235, 242, 250));

        g.setColor(new Color(15, 20, 28, 150));
        g.fillRoundRect(x, y, width, 7, 4, 4);

        int fillW = (int) (width * (FastMath.clamp(pct, 0f, 100f) / 100.0f));
        if (fillW > 0) {
            g.setColor(fillCol);
            g.fillRoundRect(x, y, fillW, 7, 4, 4);
        }
    }

    private void refreshMinimalistHud() {
        int w = settings.getWidth();
        int h = settings.getHeight();

        hudG2d.setBackground(new Color(0, 0, 0, 0));
        hudG2d.clearRect(0, 0, w, h);

        int leftX = 42;
        int baseY = h - 42;

        hudG2d.setFont(new Font("Malgun Gothic", Font.BOLD, 24));
        drawShadowedText(hudG2d, String.format("%,.0f m", vitals.getCurrentAltitude()), leftX, baseY - 125, Color.WHITE);

        int hours = (int) gameTimeHours;
        int minutes = (int) ((gameTimeHours - hours) * 60.0f);
        hudG2d.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        Color tempCol = vitals.getBodyTemp() < 35.2f ? new Color(255, 110, 110) : new Color(210, 232, 250);
        drawShadowedText(hudG2d, String.format("체온 %.1f°C   ·   기온 %.1f°C   ·   %02d:%02d (%s)",
                        vitals.getBodyTemp(), vitals.getAmbientTemp(), hours, minutes, currentWeather.label),
                leftX, baseY - 100, tempCol);

        int barW = 220;
        drawMinimalBar(hudG2d, leftX, baseY - 72, barW, vitals.getHp(), "체력", new Color(235, 75, 75));
        drawMinimalBar(hudG2d, leftX, baseY - 40, barW, vitals.getStamina(),
                vitals.getHp() < 50f ? "스태미나 (부상)" : "스태미나", new Color(245, 195, 65));
        drawMinimalBar(hudG2d, leftX, baseY - 8, barW, vitals.getWater(), "수분", new Color(75, 175, 245));

        int rightX = w - 340;
        String[] items = {
                "[1] 피켈",
                "[2] 에너지바 x" + vitals.getEnergyBarCount(),
                "[3] 수통 x" + vitals.getWaterCount()
        };
        for (int i = 0; i < 3; i++) {
            boolean sel = (activeSlot == i + 1);
            int ix = rightX + i * 105;
            hudG2d.setFont(new Font("Malgun Gothic", sel ? Font.BOLD : Font.PLAIN, 13));
            drawShadowedText(hudG2d, items[i], ix, baseY - 12, sel ? Color.WHITE : new Color(185, 200, 215));
            if (sel) {
                hudG2d.setColor(new Color(245, 195, 65));
                hudG2d.fillRect(ix, baseY - 5, 75, 3);
            }
        }

        String prompt = getNearestInteractionPrompt();
        String notice = climberControl.getStatusMessage();
        String centerMsg = !notice.isEmpty() ? notice : prompt;
        if (!centerMsg.isEmpty()) {
            hudG2d.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
            int msgW = hudG2d.getFontMetrics().stringWidth(centerMsg);
            drawShadowedText(hudG2d, centerMsg, w / 2 - msgW / 2, h / 2 - 75, new Color(255, 235, 150));
        }

        hudG2d.setColor(new Color(255, 255, 255, 200));
        hudG2d.fillOval(w / 2 - 2, h / 2 - 2, 4, 4);

        if (hudTexture.getImage() != null) {
            hudTexture.getImage().dispose();
        }
        Image jmeHudImg = awtLoader.load(hudBuffer, true);
        hudTexture.setImage(jmeHudImg);
    }

    private String getNearestInteractionPrompt() {
        if (vitals.isDead()) {
            return "조난 행동불능 — [Y] 키를 눌러 베이스캠프로 귀환";
        }
        Vector3f pos = playerNode.getLocalTranslation();
        if (pos.distance(summitPosition) < 22.0f && vitals.getCurrentAltitude() > 8500f) {
            return "에베레스트 8,848m 최정상 등정 성공!";
        }
        for (int i = 0; i < supplyDepotPositions.length; i++) {
            if (supplyDepotPositions[i] != null && pos.distance(supplyDepotPositions[i]) < 8.5f) {
                return "[E] 보급품 충전 및 휴식";
            }
        }
        return "";
    }

    private void tryResupply() {
        Vector3f pos = playerNode.getLocalTranslation();
        for (Vector3f depot : supplyDepotPositions) {
            if (depot != null && pos.distance(depot) < 8.5f) {
                vitals.resupplyAtDepot();
                climberControl.showNotice("보급 완료 (에너지바 x3, 수통 x3)");
                return;
            }
        }
        climberControl.showNotice("근처에 보급 배럴이 없습니다");
    }

    private void setupInputs() {
        inputManager.addMapping("Forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Backward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Sprint", new KeyTrigger(KeyInput.KEY_LSHIFT));
        inputManager.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("Resupply", new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping("ResetCamp", new KeyTrigger(KeyInput.KEY_Y));
        inputManager.addMapping("UseGear", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("Gear1", new KeyTrigger(KeyInput.KEY_1));
        inputManager.addMapping("Gear2", new KeyTrigger(KeyInput.KEY_2));
        inputManager.addMapping("Gear3", new KeyTrigger(KeyInput.KEY_3));

        inputManager.addMapping("LookLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("LookRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("LookUp", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("LookDown", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        inputManager.addListener(this,
                "Forward", "Backward", "Left", "Right", "Sprint", "Jump",
                "Resupply", "ResetCamp", "UseGear", "Gear1", "Gear2", "Gear3",
                "LookLeft", "LookRight", "LookUp", "LookDown");
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        float sensitivity = 1.75f;
        switch (name) {
            case "LookLeft" -> climberControl.rotateCamera(value * sensitivity, 0f);
            case "LookRight" -> climberControl.rotateCamera(-value * sensitivity, 0f);
            // [버그 수정] 마우스를 위로 올리면 위(-pitch)를 보고, 아래로 내리면 아래(+pitch)를 보도록 부호 교정!
            case "LookUp" -> climberControl.rotateCamera(0f, -value * sensitivity);
            case "LookDown" -> climberControl.rotateCamera(0f, value * sensitivity);
        }
    }

    private void switchGear(ClimbingGear newGear, int slotIndex) {
        viewModelNode.detachChild(activeGear);
        activeGear = newGear;
        activeSlot = slotIndex;
        viewModelNode.attachChild(activeGear);
        climberControl.setCurrentGear(activeGear);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (inputManager.isCursorVisible()) {
            inputManager.setCursorVisible(false);
        }
        if ("Gear1".equals(name) && isPressed) switchGear(iceAxe, 1);
        else if ("Gear2".equals(name) && isPressed) switchGear(energyBar, 2);
        else if ("Gear3".equals(name) && isPressed) switchGear(waterBottle, 3);
        else if ("Resupply".equals(name) && isPressed) tryResupply();
        else if ("ResetCamp".equals(name) && isPressed) warpPlayerToCamp();
        else climberControl.setInput(name, isPressed);
    }

    private void updateDayNightCycle(float tpf) {
        gameTimeHours += tpf / SECONDS_PER_GAME_HOUR;
        if (gameTimeHours >= 24.0f) {
            gameTimeHours -= 24.0f;
            dayCount++;
        }

        float sunAngle = ((gameTimeHours - 6.0f) / 24.0f) * FastMath.TWO_PI;
        float sunHeight = FastMath.sin(sunAngle);
        float brightness = FastMath.clamp(sunHeight + 0.35f, 0.22f, 1.0f);

        sunLight.setDirection(new Vector3f(0.45f, -Math.max(0.28f, Math.abs(sunHeight)), -0.42f).normalizeLocal());

        if (sunHeight > -0.15f && sunHeight < 0.25f) {
            skyColor.set(0.76f * brightness, 0.64f * brightness, 0.72f * brightness, 1.0f);
            sunLight.setColor(new ColorRGBA(1.0f, 0.80f, 0.62f, 1.0f).mult(brightness));
            skyBounceLight.setColor(new ColorRGBA(0.45f, 0.52f, 0.68f, 1.0f).mult(brightness));
        } else if (sunHeight >= 0.25f) {
            // 화사하고 맑은 알파인 설원 주간 하늘 및 풍부한 반사광
            skyColor.set(0.68f * brightness, 0.80f * brightness, 0.92f * brightness, 1.0f);
            sunLight.setColor(ColorRGBA.White.mult(brightness * 1.05f));
            skyBounceLight.setColor(new ColorRGBA(0.55f, 0.68f, 0.85f, 1.0f).mult(brightness));
        } else {
            skyColor.set(0.08f, 0.12f, 0.22f, 1.0f);
            sunLight.setColor(new ColorRGBA(0.26f, 0.34f, 0.48f, 1.0f));
            skyBounceLight.setColor(new ColorRGBA(0.14f, 0.20f, 0.32f, 1.0f));
        }

        ambientLight.setColor(skyColor.mult(0.88f));
        viewPort.setBackgroundColor(skyColor);
        fogFilter.setFogColor(skyColor);
    }

    private void updateWeatherSystem(float tpf) {
        weatherTimer += tpf;
        if (weatherTimer >= nextWeatherDuration) {
            weatherTimer = 0.0f;
            int pick = FastMath.nextRandomInt(0, 99);
            boolean highAltitude = vitals.getCurrentAltitude() > 6500f;
            if (pick < (highAltitude ? 38 : 22)) {
                currentWeather = WeatherType.BLIZZARD;
                nextWeatherDuration = 26.0f;
            } else if (pick < 65) {
                currentWeather = WeatherType.SNOWING;
                nextWeatherDuration = 35.0f;
            } else {
                currentWeather = WeatherType.CLEAR;
                nextWeatherDuration = 48.0f;
            }
        }

        currentFogDensity = FastMath.interpolateLinear(tpf * 0.7f, currentFogDensity, currentWeather.targetFogDensity);
        currentFogDist = FastMath.interpolateLinear(tpf * 0.7f, currentFogDist, currentWeather.targetFogDistance);
        fogFilter.setFogDensity(currentFogDensity);
        fogFilter.setFogDistance(currentFogDist);

        snowEmitter.setLocalTranslation(cam.getLocation());
        if (currentWeather == WeatherType.BLIZZARD) snowEmitter.setParticlesPerSec(1000);
        else if (currentWeather == WeatherType.SNOWING) snowEmitter.setParticlesPerSec(320);
        else snowEmitter.setParticlesPerSec(0);

        boolean isNight = (gameTimeHours < 6.0f || gameTimeHours >= 19.5f);
        climberControl.setWeatherTempOffset(currentWeather.tempOffset + (isNight ? -7.5f : 0.0f));
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (inputManager.isCursorVisible()) {
            inputManager.setCursorVisible(false);
        }

        updateDayNightCycle(tpf);
        updateWeatherSystem(tpf);

        viewModelNode.setLocalTranslation(cam.getLocation());
        viewModelNode.setLocalRotation(cam.getRotation());

        hudRefreshTimer += tpf;
        if (hudRefreshTimer >= 0.10f) {
            hudRefreshTimer = 0.0f;
            refreshMinimalistHud();
        }
    }
}