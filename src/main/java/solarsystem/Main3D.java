package solarsystem;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class Main3D extends Application {

    private Group root;
    private Scene scene;
    private PerspectiveCamera camera;
    private SolarSystem solarSystem;
    private double timeSpeed = 1.0;
    private boolean isRunning = true;
    private List<Sphere> planetSpheres = new ArrayList<>();
    private List<Sphere> stars = new ArrayList<>();
    private List<OrbitLine> orbitLines = new ArrayList<>();
    private Random random = new Random();

    private Sphere comet;
    private boolean cometActive = false;
    private int targetIndex = -2; // -2 = не активно, -1 = Солнце, 0–7 = планеты

    private List<Sphere> cometTrail = new ArrayList<>();

    private boolean wPressed = false, aPressed = false, sPressed = false, dPressed = false;
    private boolean qPressed = false, ePressed = false;
    private boolean rightMousePressed = false;
    private double moveSpeed = 12.0;

    private final Rotate cameraRotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate cameraRotateY = new Rotate(180, Rotate.Y_AXIS);

    private double mouseOldX, mouseOldY;

    private class OrbitLine {
        List<Sphere> dots = new ArrayList<>();
        double radius;

        OrbitLine(double radius) {
            this.radius = radius;
        }
    }

    private class Fragment {
        Sphere sphere;
        double vx, vy, vz;
        double life = 1.0;

        Fragment(Sphere sphere, double vx, double vy, double vz) {
            this.sphere = sphere;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }
    }

    private List<Fragment> activeFragments = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        root = new Group();
        scene = new Scene(root, 1400, 900, true);
        scene.setFill(Color.BLACK);

        solarSystem = new SolarSystem();

        setupCamera();
        setupLights();
        createSun();
        createPlanets();
        createOrbits();
        createStars();
        setupControls();

        startAnimation();
        startMovementTimer();
        startPhysicsTimer();

        primaryStage.setTitle("3D Солнечная система — Метеоритная атака");
        primaryStage.setScene(scene);
        primaryStage.show();

        printControls();
    }

    private void printControls() {
        System.out.println("\nУправление:");
        System.out.println("  ПРАВАЯ КНОПКА + движение мыши → осмотр");
        System.out.println("  W/A/S/D → движение,   Q/E → вверх/вниз");
        System.out.println("  ПРОБЕЛ → пауза/старт");
        System.out.println("  + / - → скорость времени");
        System.out.println("  1–8   → комета в планету (1=Меркурий ... 8=Нептун)");
        System.out.println("  0     → комета в СОЛНЦЕ → СВЕРХНОВАЯ");
        System.out.println("  R     → сброс камеры");
        System.out.println("  X     → перезапустить систему\n");
    }

    private void setupCamera() {
        camera = new PerspectiveCamera(true);
        camera.setNearClip(1.0);
        camera.setFarClip(60000);
        camera.setTranslateZ(2800);
        camera.getTransforms().addAll(cameraRotateY, cameraRotateX);
        scene.setCamera(camera);
    }

    private void setupLights() {
        PointLight sunLight = new PointLight(Color.WHITE);
        sunLight.setTranslateX(0);
        sunLight.setTranslateY(0);
        sunLight.setTranslateZ(0);
        sunLight.setMaxRange(40000);

        AmbientLight ambient = new AmbientLight(Color.gray(0.12));
        root.getChildren().addAll(sunLight, ambient);
    }

    private void createSun() {
        Sphere sunSphere = new Sphere(80);
        PhongMaterial mat = new PhongMaterial();
        try {
            Image tex = new Image(getClass().getResourceAsStream("/images/sun.jpg"));
            mat.setDiffuseMap(tex);
            mat.setSelfIlluminationMap(tex);
        } catch (Exception ignored) {
            mat.setDiffuseColor(Color.ORANGE.brighter());
        }
        sunSphere.setMaterial(mat);
        root.getChildren().add(sunSphere);

        PointLight glow = new PointLight(Color.YELLOW.brighter());
        glow.setMaxRange(20000);
        root.getChildren().add(glow);
    }

    private void createPlanets() {
        String[] textures = {
                "mercury.jpg", "venus.jpg", "earth.jpg", "mars.jpg",
                "jupiter.jpg", "saturn.jpg", "uranus.jpg", "neptune.jpg"
        };

        planetSpheres.clear();
        for (int i = 0; i < solarSystem.getPlanets().size(); i++) {
            CelestialBody body = solarSystem.getPlanets().get(i);
            Sphere sphere = new Sphere(body.getRadius() * 1.0);

            PhongMaterial mat = new PhongMaterial();
            try {
                Image tex = new Image(getClass().getResourceAsStream("/images/" + textures[i]));
                mat.setDiffuseMap(tex);
            } catch (Exception e) {
                mat.setDiffuseColor(body.getColor());
            }
            mat.setSpecularColor(Color.WHITE);
            mat.setSpecularPower(28);
            sphere.setMaterial(mat);

            updatePlanetPosition(sphere, body);
            root.getChildren().add(sphere);
            planetSpheres.add(sphere);
        }
    }

    private void createOrbits() {
        orbitLines.clear();
        for (CelestialBody p : solarSystem.getPlanets()) {
            double r = p.getOrbitRadius();
            OrbitLine orbit = new OrbitLine(r);

            for (int deg = 0; deg < 360; deg += 4) {
                double rad = Math.toRadians(deg);
                Sphere dot = new Sphere(0.8);
                PhongMaterial m = new PhongMaterial(Color.rgb(160, 200, 255, 0.3));
                dot.setMaterial(m);
                dot.setTranslateX(r * Math.cos(rad));
                dot.setTranslateZ(r * Math.sin(rad));
                root.getChildren().add(dot);
                orbit.dots.add(dot);
            }

            for (int deg = 0; deg < 360; deg += 45) {
                double rad = Math.toRadians(deg);
                Sphere marker = new Sphere(1.6);
                PhongMaterial mm = new PhongMaterial(Color.rgb(240, 255, 255, 0.85));
                marker.setMaterial(mm);
                marker.setTranslateX(r * Math.cos(rad));
                marker.setTranslateZ(r * Math.sin(rad));
                root.getChildren().add(marker);
                orbit.dots.add(marker);
            }

            orbitLines.add(orbit);
        }
    }

    private void createStars() {
        for (int i = 0; i < 4500; i++) {
            double x = (random.nextDouble() - 0.5) * 30000;
            double y = (random.nextDouble() - 0.5) * 30000;
            double z = (random.nextDouble() - 0.5) * 30000;
            double size = 0.3 + random.nextDouble() * 2.5;

            Sphere star = new Sphere(size);
            Color c = switch (random.nextInt(7)) {
                case 0,1 -> Color.WHITE;
                case 2 -> Color.rgb(220, 240, 255);
                case 3 -> Color.rgb(255, 245, 200);
                case 4 -> Color.rgb(255, 210, 190);
                case 5 -> Color.rgb(200, 220, 255);
                default -> Color.rgb(240, 240, 255);
            };

            star.setMaterial(new PhongMaterial(c));
            star.setTranslateX(x);
            star.setTranslateY(y);
            star.setTranslateZ(z);
            root.getChildren().add(star);
            stars.add(star);
        }
    }

    private void launchComet(int target) {
        if (cometActive) return;

        targetIndex = target - 1;

        comet = new Sphere(42);

        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseColor(Color.rgb(255, 210, 60));
        mat.setSpecularColor(Color.WHITE);
        mat.setSpecularPower(220);
        comet.setMaterial(mat);

        Glow glow = new Glow(1.0);
        comet.setEffect(glow);

        double startDist = (target == 0) ? 8500 : 5800;
        double angle = random.nextDouble() * Math.PI * 2;
        double height = (random.nextDouble() - 0.5) * 3000;

        double sx, sy, sz;
        if (target == 0) {
            sx = Math.cos(angle) * startDist;
            sy = height;
            sz = Math.sin(angle) * startDist;
            System.out.println("☄️ Комета летит в СОЛНЦЕ!");
        } else {
            CelestialBody tgt = solarSystem.getPlanets().get(target - 1);
            sx = tgt.getX() + Math.cos(angle) * startDist;
            sy = tgt.getY() + height;
            sz = tgt.getZ() + Math.sin(angle) * startDist;
            System.out.println("☄️ Комета летит к планете " + target + " (" + getPlanetName(target) + ")");
        }

        comet.setTranslateX(sx);
        comet.setTranslateY(sy);
        comet.setTranslateZ(sz);

        cometActive = true;
        cometTrail.clear();
    }

    private String getPlanetName(int num) {
        String[] names = {"Меркурий", "Венера", "Земля", "Марс", "Юпитер", "Сатурн", "Уран", "Нептун"};
        return (num >= 1 && num <= 8) ? names[num - 1] : "Солнце";
    }

    private void explodePlanet(int index) {
        if (index < 0 || index >= planetSpheres.size()) return;

        Sphere planet = planetSpheres.get(index);
        CelestialBody body = solarSystem.getPlanets().get(index);

        double px = planet.getTranslateX();
        double py = planet.getTranslateY();
        double pz = planet.getTranslateZ();
        double pr = body.getRadius();

        root.getChildren().remove(planet);
        planetSpheres.remove(index);

        if (index < orbitLines.size()) {
            OrbitLine orbit = orbitLines.get(index);
            for (Sphere dot : orbit.dots) {
                root.getChildren().remove(dot);
            }
            orbitLines.remove(index);
        }


        int count = 700;
        for (int i = 0; i < count; i++) {
            double size = 0.6 + random.nextDouble() * pr * 1.6;
            Sphere frag = new Sphere(size);

            Color base = body.getColor();
            double factor = 0.6 + random.nextDouble() * 0.8;
            double r = Math.max(0.0, Math.min(1.0, base.getRed() * factor));
            double g = Math.max(0.0, Math.min(1.0, base.getGreen() * factor));
            double b = Math.max(0.0, Math.min(1.0, base.getBlue() * factor));
            double opacity = 0.7 + random.nextDouble() * 0.25;

            Color c = new Color(r, g, b, opacity);

            PhongMaterial m = new PhongMaterial(c);
            m.setSpecularColor(Color.WHITE);
            m.setSpecularPower(20 + random.nextDouble() * 40);
            frag.setMaterial(m);

            double spread = pr * 2.5;
            frag.setTranslateX(px + (random.nextDouble()-0.5)*spread);
            frag.setTranslateY(py + (random.nextDouble()-0.5)*spread);
            frag.setTranslateZ(pz + (random.nextDouble()-0.5)*spread);

            root.getChildren().add(frag);

            double speed = 16 + random.nextDouble() * 50;
            double a1 = random.nextDouble() * Math.PI * 2;
            double a2 = random.nextDouble() * Math.PI;

            double vx = Math.sin(a1) * Math.cos(a2) * speed;
            double vy = Math.sin(a1) * Math.sin(a2) * speed * 0.8;
            double vz = Math.cos(a1) * speed;

            activeFragments.add(new Fragment(frag, vx, vy, vz));
        }

        clearCometTrail();
        System.out.println(" " + getPlanetName(index + 1) + " уничтожена");
    }

    private void explodeSun() {
        Sphere flash = new Sphere(600);
        PhongMaterial flashMat = new PhongMaterial(Color.WHITE);
        flash.setMaterial(flashMat);
        flash.setEffect(new Glow(1.2));
        root.getChildren().add(flash);

        root.getChildren().removeIf(node -> node instanceof Sphere && ((Sphere) node).getRadius() > 70);

        planetSpheres.clear();
        for (OrbitLine ol : orbitLines) {
            for (Sphere dot : ol.dots) {
                root.getChildren().remove(dot);
            }
        }
        orbitLines.clear();

        List<Node> toRemove = new ArrayList<>();
        for (Node node : root.getChildren()) {
            if (node instanceof Sphere && node != flash) {
                toRemove.add(node);
            }
        }
        root.getChildren().removeAll(toRemove);

        int waveCount = 2200;
        for (int i = 0; i < waveCount; i++) {
            double size = 2.0 + random.nextDouble() * 20;
            Sphere frag = new Sphere(size);

            double t = (double) i / waveCount;
            int red   = 255;
            int green = Math.max(0, Math.min(255, (int)(240 * (1 - t * 1.4))));
            int blue  = Math.max(0, Math.min(255, (int)(120 * (1 - t * 2.0))));
            double opacity = Math.max(0.0, Math.min(1.0, 0.9 - t * 0.7));

            Color waveColor = Color.rgb(red, green, blue, opacity);

            PhongMaterial m = new PhongMaterial(waveColor);
            frag.setMaterial(m);

            double angle1 = random.nextDouble() * Math.PI * 2;
            double angle2 = random.nextDouble() * Math.PI;
            double dist = 250 + random.nextDouble() * 1000;

            frag.setTranslateX(Math.sin(angle1) * Math.cos(angle2) * dist);
            frag.setTranslateY(Math.sin(angle1) * Math.sin(angle2) * dist * 0.7);
            frag.setTranslateZ(Math.cos(angle1) * dist);

            root.getChildren().add(frag);

            double speed = 40 + random.nextDouble() * 100;
            double vx = Math.sin(angle1) * Math.cos(angle2) * speed;
            double vy = Math.sin(angle1) * Math.sin(angle2) * speed * 0.7;
            double vz = Math.cos(angle1) * speed;

            activeFragments.add(new Fragment(frag, vx, vy, vz));
        }

        System.out.println("☀ СОЛНЦЕ УНИЧТОЖЕНО! СВЕРХНОВАЯ!");

        Timeline fade = new Timeline(
                new KeyFrame(Duration.seconds(0.7), e -> {
                    flash.setScaleX(5);
                    flash.setScaleY(5);
                    flash.setScaleZ(5);
                }),
                new KeyFrame(Duration.seconds(4.0), e -> root.getChildren().remove(flash))
        );
        fade.play();

        clearCometTrail();
    }

    private void clearCometTrail() {
        for (Sphere t : cometTrail) {
            root.getChildren().remove(t);
        }
        cometTrail.clear();
    }

    private void resetSystem() {
        root.getChildren().clear();
        activeFragments.clear();
        planetSpheres.clear();
        orbitLines.clear();
        cometActive = false;
        clearCometTrail();

        if (comet != null) {
            root.getChildren().remove(comet);
            comet = null;
        }

        solarSystem = new SolarSystem();
        setupLights();
        createSun();
        createPlanets();
        createOrbits();
        createStars();

        System.out.println(" Система перезапущена");
    }

    private void startPhysicsTimer() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (cometActive && comet != null) {
                    double tx = 0, ty = 0, tz = 0;
                    double collisionRadius = (targetIndex == -1) ? 130 : 50;

                    if (targetIndex == -1) {
                        tx = 0; ty = 0; tz = 0;
                    } else if (targetIndex >= 0 && targetIndex < planetSpheres.size()) {
                        Sphere tgt = planetSpheres.get(targetIndex);
                        tx = tgt.getTranslateX();
                        ty = tgt.getTranslateY();
                        tz = tgt.getTranslateZ();
                        CelestialBody b = solarSystem.getPlanets().get(targetIndex);
                        collisionRadius = b.getRadius() + 45;
                    } else {
                        cometActive = false;
                        return;
                    }

                    double cx = comet.getTranslateX();
                    double cy = comet.getTranslateY();
                    double cz = comet.getTranslateZ();

                    double dx = tx - cx;
                    double dy = ty - cy;
                    double dz = tz - cz;
                    double dist = Math.hypot(dx, Math.hypot(dy, dz));

                    if (dist < collisionRadius) {
                        root.getChildren().remove(comet);
                        clearCometTrail();
                        cometActive = false;
                        comet = null;

                        if (targetIndex == -1) {
                            explodeSun();
                        } else {
                            explodePlanet(targetIndex);
                        }
                    } else {
                        // Плавное движение
                        double baseSpeed = (targetIndex == -1) ? 5.0 : 6.8;
                        double speedFactor = Math.min(1.0, dist / 2200.0);
                        double speed = baseSpeed * (0.45 + speedFactor * 0.55);

                        double vx = (dx / dist) * speed;
                        double vy = (dy / dist) * speed;
                        double vz = (dz / dist) * speed;

                        double curve = 0.14 * speed * (random.nextDouble() - 0.5);
                        vx += curve * (dz / dist);
                        vz -= curve * (dx / dist);

                        comet.setTranslateX(cx + vx);
                        comet.setTranslateY(cy + vy);
                        comet.setTranslateZ(cz + vz);

                        comet.setRotate(comet.getRotate() + 14);

                        // Сплошной хвост — очень много мелких частиц
                        int particlesThisFrame = 4 + random.nextInt(4); // 4–7 за кадр
                        for (int p = 0; p < particlesThisFrame; p++) {
                            if (cometTrail.size() >= 300) break;

                            double trailSize = 3.5 + random.nextDouble() * 5.5;
                            Sphere trailPart = new Sphere(trailSize);

                            double opacity = 0.85 - (cometTrail.size() * 0.0028);
                            opacity = Math.max(0.04, Math.min(0.98, opacity));

                            int red   = 255;
                            int green = 130 + (int)(random.nextDouble() * 100);
                            int blue  = 0 + (int)(random.nextDouble() * 80);

                            Color trailColor = Color.rgb(red, green, blue, opacity);

                            PhongMaterial trailMat = new PhongMaterial(trailColor);
                            trailPart.setMaterial(trailMat);

                            double back = 1.2 + random.nextDouble() * 2.0;
                            double scatter = random.nextDouble() * 12 - 6;

                            trailPart.setTranslateX(cx - vx * back + scatter * (dz / dist));
                            trailPart.setTranslateY(cy - vy * back + scatter * 0.8);
                            trailPart.setTranslateZ(cz - vz * back - scatter * (dx / dist));

                            trailPart.setEffect(new Glow(0.95 + random.nextDouble() * 0.25));

                            root.getChildren().add(trailPart);
                            cometTrail.add(trailPart);
                        }

                        while (cometTrail.size() > 300) {
                            Sphere old = cometTrail.remove(0);
                            root.getChildren().remove(old);
                        }
                    }
                }

                Iterator<Fragment> it = activeFragments.iterator();
                while (it.hasNext()) {
                    Fragment f = it.next();
                    f.sphere.setTranslateX(f.sphere.getTranslateX() + f.vx);
                    f.sphere.setTranslateY(f.sphere.getTranslateY() + f.vy);
                    f.sphere.setTranslateZ(f.sphere.getTranslateZ() + f.vz);
                    f.sphere.setRotate(f.sphere.getRotate() + 4);
                    f.vx *= 0.986;
                    f.vy *= 0.986;
                    f.vz *= 0.986;
                    f.life -= (f.sphere.getRadius() > 8) ? 0.0006 : 0.0012;
                    if (f.life <= 0) {
                        root.getChildren().remove(f.sphere);
                        it.remove();
                    }
                }
            }
        }.start();
    }

    private void setupControls() {
        scene.setOnMousePressed(e -> {
            if (e.isSecondaryButtonDown()) {
                rightMousePressed = true;
                mouseOldX = e.getSceneX();
                mouseOldY = e.getSceneY();
            }
        });

        scene.setOnMouseReleased(e -> rightMousePressed = false);

        scene.setOnMouseDragged(e -> {
            if (rightMousePressed) {
                double dx = e.getSceneX() - mouseOldX;
                double dy = e.getSceneY() - mouseOldY;
                cameraRotateY.setAngle(cameraRotateY.getAngle() - dx * 0.4);
                cameraRotateX.setAngle(cameraRotateX.getAngle() - dy * 0.4);
                cameraRotateX.setAngle(Math.max(-89, Math.min(89, cameraRotateX.getAngle())));
                mouseOldX = e.getSceneX();
                mouseOldY = e.getSceneY();
            }
        });

        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case SPACE:
                    isRunning = !isRunning;
                    System.out.println(isRunning ? "▶ Запущено" : "⏸ Пауза");
                    break;
                case ADD:
                case EQUALS:
                    timeSpeed *= 1.6;
                    System.out.println("Скорость × " + String.format("%.1f", timeSpeed));
                    break;
                case SUBTRACT:
                case MINUS:
                    timeSpeed /= 1.6;
                    System.out.println("Скорость × " + String.format("%.1f", timeSpeed));
                    break;
                case R:
                    camera.setTranslateX(0);
                    camera.setTranslateY(0);
                    camera.setTranslateZ(2800);
                    cameraRotateY.setAngle(180);
                    cameraRotateX.setAngle(0);
                    System.out.println("Вид сброшен");
                    break;
                case X:
                    resetSystem();
                    break;
                case DIGIT0:
                    launchComet(0);
                    break;
                case DIGIT1:
                    launchComet(1);
                    break;
                case DIGIT2:
                    launchComet(2);
                    break;
                case DIGIT3:
                    launchComet(3);
                    break;
                case DIGIT4:
                    launchComet(4);
                    break;
                case DIGIT5:
                    launchComet(5);
                    break;
                case DIGIT6:
                    launchComet(6);
                    break;
                case DIGIT7:
                    launchComet(7);
                    break;
                case DIGIT8:
                    launchComet(8);
                    break;
                case W: wPressed = true; break;
                case A: aPressed = true; break;
                case S: sPressed = true; break;
                case D: dPressed = true; break;
                case Q: qPressed = true; break;
                case E: ePressed = true; break;
            }
        });

        scene.setOnKeyReleased(e -> {
            switch (e.getCode()) {
                case W: wPressed = false; break;
                case A: aPressed = false; break;
                case S: sPressed = false; break;
                case D: dPressed = false; break;
                case Q: qPressed = false; break;
                case E: ePressed = false; break;
            }
        });
    }

    private void startAnimation() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (isRunning) {
                    solarSystem.updatePositions(timeSpeed);
                    for (int i = 0; i < planetSpheres.size(); i++) {
                        updatePlanetPosition(planetSpheres.get(i), solarSystem.getPlanets().get(i));
                    }
                }
            }
        }.start();
    }

    private void startMovementTimer() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                double speed = moveSpeed;
                double ay = Math.toRadians(cameraRotateY.getAngle());
                double ax = Math.toRadians(cameraRotateX.getAngle());

                double mx = 0, my = 0, mz = 0;

                if (wPressed) {
                    mx += Math.sin(ay) * Math.cos(ax) * speed;
                    my += Math.sin(ax) * speed;
                    mz += Math.cos(ay) * Math.cos(ax) * speed;
                }
                if (sPressed) {
                    mx -= Math.sin(ay) * Math.cos(ax) * speed;
                    my -= Math.sin(ax) * speed;
                    mz -= Math.cos(ay) * Math.cos(ax) * speed;
                }
                if (aPressed) {
                    mx -= Math.cos(ay) * speed;
                    mz += Math.sin(ay) * speed;
                }
                if (dPressed) {
                    mx += Math.cos(ay) * speed;
                    mz -= Math.sin(ay) * speed;
                }
                if (qPressed) my -= speed;
                if (ePressed) my += speed;

                camera.setTranslateX(camera.getTranslateX() + mx);
                camera.setTranslateY(camera.getTranslateY() + my);
                camera.setTranslateZ(camera.getTranslateZ() + mz);
            }
        }.start();
    }

    private void updatePlanetPosition(Sphere s, CelestialBody b) {
        s.setTranslateX(b.getX());
        s.setTranslateY(b.getY());
        s.setTranslateZ(b.getZ());
    }

    public static void main(String[] args) {
        launch(args);
    }
}