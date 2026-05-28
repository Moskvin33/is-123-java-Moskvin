package solarsystem;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.control.TextField;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import solarsystem.patterns.factory.*;
import solarsystem.patterns.observer.*;

public class Main3D extends Application {
    private Group root3D;
    private Pane rootPane;
    private Scene scene;
    private PerspectiveCamera camera;
    private SolarSystem solarSystem;
    private double timeSpeed = 0.8;
    private boolean isRunning = true;
    private List<Sphere> planetSpheres = new ArrayList<>();
    private List<Sphere> stars = new ArrayList<>();
    private List<OrbitLine> orbitLines = new ArrayList<>();
    private Random random = new Random();

    private Group cometGroup;
    private Sphere cometNucleus, cometInnerGlow, cometOuterGlow;
    private List<Box> cometTail;
    private List<Sphere> tailGlowParticles;
    private boolean cometActive = false;
    private int targetIndex = -2;
    private double cometHeat = 0.5;
    private List<Sphere> cometTrailParticles = new ArrayList<>();
    private List<Sphere> asteroids = new ArrayList<>();
    private List<Double> asteroidSpeeds = new ArrayList<>();
    private List<Sphere> starDust = new ArrayList<>();

    private Text hudText;
    private Rectangle hudBg;
    private int destroyedPlanets = 0;

    private Group blackHoleGroup;
    private Sphere blackHoleSphere, blackHoleGlow;
    private Cylinder accretionDisk;
    private PointLight blackHoleLight;
    private boolean blackHoleActive = false;
    private double blackHoleX, blackHoleY, blackHoleZ;
    private List<Sphere> blackHoleParticles = new ArrayList<>();

    private enum CameraMode { FREE, FOLLOW_COMET }
    private CameraMode cameraMode = CameraMode.FREE;
    private double followDistance = 300, followHeight = 0, followYaw = 0, followPitch = 0;
    private boolean wPressed, aPressed, sPressed, dPressed, qPressed, ePressed;
    private boolean rightMousePressed;
    private double moveSpeed = 12.0;
    private final Rotate cameraRotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate cameraRotateY = new Rotate(180, Rotate.Y_AXIS);
    private double mouseOldX, mouseOldY;
    private PointLight cometLight, cometFireLight;
    private List<Rotate> planetRotations = new ArrayList<>();
    private Group saturnRings;
    private Sphere earthMoon;
    private List<GravitationalImpulse> activeImpulses = new ArrayList<>();
    private List<Fragment> activeFragments = new ArrayList<>();
    private double physicsTimeAcc = 0;
    private final double PHYSICS_DT = 0.016;

    // === ПАТТЕРНЫ ===
    private Map<String, CelestialFactory> factories = new HashMap<>();
    private List<SimulationObserver> observers = new ArrayList<>();

    // === БД ===
    private static String DB_URL;
    private static final String DB_NAME = "solar_system.db";
    private Timeline dbPoller;
    private final Set<Integer> processedCommandIds = new HashSet<>();

    // Консоль
    private TextField consoleInput;
    private boolean consoleVisible = false;

    private class OrbitLine { List<Sphere> dots = new ArrayList<>(); double radius; OrbitLine(double r){ this.radius=r; } }
    private class Fragment { Sphere sphere; double vx,vy,vz,life; Fragment(Sphere s,double vx,double vy,double vz){ this.sphere=s; this.vx=vx; this.vy=vy; this.vz=vz; this.life=1.0; } }
    private class GravitationalImpulse { double x,y,z,strength,radius,life; GravitationalImpulse(double x,double y,double z,double s,double r){ this.x=x;this.y=y;this.z=z;this.strength=s;this.radius=r;this.life=3.0; } }

    @Override
    public void start(Stage primaryStage) {
        initSolarDb();

        rootPane = new Pane(); root3D = new Group();
        SubScene subScene3D = new SubScene(root3D, 1400, 900, true, SceneAntialiasing.BALANCED);
        subScene3D.setFill(Color.BLACK);
        subScene3D.widthProperty().bind(primaryStage.widthProperty());
        subScene3D.heightProperty().bind(primaryStage.heightProperty());
        scene = new Scene(rootPane, 1400, 900); scene.setFill(Color.BLACK);

        solarSystem = new SolarSystem();
        setupCamera(); setupLights(); createSun(); createPlanets(); createOrbits();
        createStars(); createStarDust(); createAsteroidBelt();
        subScene3D.setCamera(camera); rootPane.getChildren().add(subScene3D);
        setupHUD(); setupControls(); setupConsole();

        initPatterns();
        startDbPolling();

        startGameLoop(); startMovementTimer(); startHUDUpdater();
        primaryStage.setTitle("3D Солнечная Система");
        primaryStage.setScene(scene); primaryStage.show();
        printControls();
    }

    private void setupConsole() {
        consoleInput = new TextField();
        consoleInput.setPromptText("SQL: INSERT INTO SOLAR_COMMANDS (action,name,radius,mass,orbit_radius,color_hex) VALUES ('create_planet','Test',8,25,300,'#00FF00')");
        consoleInput.setStyle("-fx-background-color: rgba(0,0,0,0.85); -fx-text-fill: #0f0; -fx-font-family: Consolas; -fx-font-size: 12px; -fx-border-color: #0f0; -fx-border-width: 1px;");
        consoleInput.setPrefWidth(750); consoleInput.setLayoutX(20); consoleInput.setLayoutY(500); consoleInput.setVisible(false);

        consoleInput.setOnAction(e -> {
            String sql = consoleInput.getText();
            if (sql != null && !sql.trim().isEmpty()) executeDbCommand(sql);
            consoleInput.clear(); consoleInput.setVisible(false); consoleVisible = false; rootPane.requestFocus();
        });
        consoleInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                consoleInput.clear(); consoleInput.setVisible(false); consoleVisible = false; rootPane.requestFocus();
            }
        });
        rootPane.getChildren().add(consoleInput);
    }

    private void executeDbCommand(String sql) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✅ SQL: " + sql.substring(0, Math.min(60, sql.length())) + "...");
        } catch (Exception e) {
            System.err.println("❌ SQL Error: " + e.getMessage());
        }
    }

    private void initSolarDb() {
        String userHome = System.getProperty("user.home");
        String sep = System.getProperty("file.separator");
        String dbPath = userHome + sep + "SolarSystem" + sep + "database" + sep;
        DB_URL = "jdbc:sqlite:" + dbPath + DB_NAME + "?busy_timeout=5000";

        try {
            Path dir = Paths.get(dbPath);
            if (!Files.exists(dir)) Files.createDirectories(dir);

            boolean dbExists = Files.exists(Paths.get(dbPath + DB_NAME));
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA foreign_keys = ON;");

                if (!dbExists) {
                    System.out.println("🌌 Creating DB...");
                    stmt.execute("CREATE TABLE IF NOT EXISTS SOLAR_COMMANDS (" +
                            "ID INTEGER PRIMARY KEY AUTOINCREMENT, ACTION TEXT, NAME TEXT, " +
                            "RADIUS REAL, MASS REAL, ORBIT_RADIUS REAL, COLOR_HEX TEXT, " +
                            "CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP)");
                    stmt.execute("CREATE TABLE IF NOT EXISTS SIMULATION_LOGS (" +
                            "ID INTEGER PRIMARY KEY AUTOINCREMENT, EVENT TEXT, DETAILS TEXT, " +
                            "TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP)");
                    System.out.println("✅ DB created: " + DB_URL);
                } else {
                    System.out.println("ℹ️ DB exists: " + DB_URL);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ DB init error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initPatterns() {
        factories = new HashMap<>();
        factories.put("planet", new PlanetFactory());
        factories.put("comet", new CometFactory());
        factories.put("blackhole", new BlackHoleFactory());

        observers = new ArrayList<>();
        observers.add(new DbLoggerObserver());
        observers.add(new ConsoleObserver());
        observers.add(new VisualEffectObserver());
        System.out.println("🔧 Patterns init: " + factories.size() + " factories, " + observers.size() + " observers");
    }

    private void notifyObservers(String eventType, Map<String, Object> data) {
        for (SimulationObserver obs : observers) {
            try { obs.onEvent(eventType, data); }
            catch (Exception e) { System.err.println("⚠️ Observer " + obs.getObserverName() + " error: " + e.getMessage()); }
        }
    }

    private void startDbPolling() {
        dbPoller = new Timeline(new KeyFrame(Duration.seconds(2), e -> pollSolarCommands()));
        dbPoller.setCycleCount(Timeline.INDEFINITE);
        dbPoller.play();
        System.out.println("📡 Polling started (2s interval)");
    }

    private void pollSolarCommands() {
        String sql = "SELECT id, action, name, radius, mass, orbit_radius, color_hex FROM SOLAR_COMMANDS ORDER BY id ASC LIMIT 10";
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                int count = 0;
                List<Integer> idsToDelete = new ArrayList<>();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (processedCommandIds.contains(id)) continue;

                    String action = rs.getString("action");
                    String name = rs.getString("name");
                    double radius = rs.getDouble("radius");
                    double mass = rs.getDouble("mass");
                    double orbit = rs.getDouble("orbit_radius");
                    String colorHex = rs.getString("color_hex");

                    processCommand(action, name, radius, mass, orbit, colorHex);
                    processedCommandIds.add(id);
                    idsToDelete.add(id);
                    count++;
                }

                for (int id : idsToDelete) {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM SOLAR_COMMANDS WHERE id = ?")) {
                        del.setInt(1, id);
                        del.executeUpdate();
                    }
                }

                if (count > 0) System.out.println("🔄 Processed " + count + " command(s)");
                return;

            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("SQLITE_BUSY") || msg.contains("database is locked"))) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        System.err.println("❌ Poll error after " + maxRetries + " retries: " + msg);
                        return;
                    }
                    try { Thread.sleep(500 * retryCount); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                } else {
                    System.err.println("❌ Poll error: " + msg);
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    private void processCommand(String action, String name, double radius, double mass, double orbit, String colorHex) {
        if (action == null) return;
        String act = action.trim().toLowerCase();
        Map<String, Object> ev = new HashMap<>();
        ev.put("action", action); ev.put("name", name);

        switch (act) {
            case "launch_comet" -> {
                int target = (int) orbit;
                if (target >= 0 && target <= 8) {
                    launchComet(target);
                    ev.put("target", target);
                    notifyObservers("COMET_LAUNCHED", ev);
                    System.out.println("🚀 Comet to: " + target);
                }
            }
            case "create_black_hole" -> {
                double bhX = 250 + random.nextDouble() * 150;
                double bhZ = 250 + random.nextDouble() * 150;
                double bhY = (random.nextDouble() - 0.5) * 50;

                CelestialBody bh = factories.get("blackhole").create("BlackHole", 25, 25000.0, 0, Color.BLACK);
                bh.x = bhX; bh.z = bhZ; bh.y = bhY;
                bh.isBlackHole = true;

                blackHoleX = bhX; blackHoleY = bhY; blackHoleZ = bhZ;

                solarSystem.addBody(bh);
                spawnBlackHoleVisual(bh);
                ev.put("type", "blackhole");
                notifyObservers("BLACK_HOLE_SPAWNED", ev);
                System.out.println(" Black hole created at: (" + bhX + ", " + bhY + ", " + bhZ + ")");
            }
            case "create_planet" -> {
                Color color = (colorHex != null && colorHex.matches("#[0-9A-Fa-f]{6}")) ? Color.web(colorHex) : Color.rgb(100,150,200);
                double r = orbit > 0 ? orbit : 200;
                CelestialBody body = factories.get("planet").create(name, radius, mass, r, color);
                solarSystem.addBody(body);

                Sphere sphere = new Sphere(body.radius);
                sphere.setMaterial(new PhongMaterial(body.color));
                sphere.setTranslateX(body.x); sphere.setTranslateZ(body.z);
                root3D.getChildren().add(sphere);
                planetSpheres.add(sphere);
                Rotate rot = new Rotate(0, Rotate.Y_AXIS);
                sphere.getTransforms().add(rot); planetRotations.add(rot);

                ev.put("radius", radius);
                notifyObservers("PLANET_CREATED", ev);
                System.out.println("✅ Planet created: " + name);
            }
            case "destroy_planet" -> {
                if (name == null) return;
                List<CelestialBody> bodies = solarSystem.getBodies();
                for (int i = 1; i < bodies.size(); i++) {
                    if (bodies.get(i).name.equals(name)) {
                        explodePlanet(i);
                        ev.put("target", name);
                        notifyObservers("EXPLOSION", ev);
                        System.out.println("💥 Destroyed: " + name);
                        return;
                    }
                }
                System.out.println("⚠️ Planet not found: " + name);
            }
            case "reset_system" -> {
                resetSystem();
                notifyObservers("SYSTEM_RESET", ev);
                System.out.println("🔄 Reset done");
            }
            default -> System.out.println("⚠️ Unknown command: '" + act + "'");
        }
    }

    private void spawnBlackHoleVisual(CelestialBody bh) {
        blackHoleActive = true;
        blackHoleGroup = new Group();

        blackHoleSphere = new Sphere(25);
        blackHoleSphere.setMaterial(new PhongMaterial(Color.rgb(2, 2, 5, 0.99)));
        blackHoleSphere.setCullFace(CullFace.NONE);

        blackHoleGlow = new Sphere(65);
        blackHoleGlow.setMaterial(new PhongMaterial(Color.rgb(60, 20, 100, 0.25)));
        blackHoleGlow.setCullFace(CullFace.NONE);
        blackHoleGlow.setEffect(new Bloom(1.2));

        accretionDisk = new Cylinder(80, 5);
        accretionDisk.setRotationAxis(Rotate.X_AXIS);
        accretionDisk.setRotate(75 + random.nextInt(20));
        accretionDisk.setMaterial(new PhongMaterial(Color.rgb(255, 160, 60, 0.5)));
        accretionDisk.setCullFace(CullFace.NONE);
        accretionDisk.setEffect(new Glow(2.0));

        blackHoleLight = new PointLight(Color.rgb(150, 80, 255, 0.8));
        blackHoleLight.setMaxRange(10000);

        blackHoleGroup.getChildren().addAll(blackHoleSphere, blackHoleGlow, accretionDisk, blackHoleLight);
        blackHoleGroup.setTranslateX(bh.x);
        blackHoleGroup.setTranslateY(bh.y);
        blackHoleGroup.setTranslateZ(bh.z);
        root3D.getChildren().add(blackHoleGroup);

        for(int i=0;i<80;i++) createBlackHoleParticleAt(bh.x, bh.y, bh.z);
        updateHUDText();
    }

    private void setupHUD() {
        hudBg = new Rectangle(340, 440);
        hudBg.setArcWidth(12); hudBg.setArcHeight(12);
        hudBg.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE, new Stop(0,Color.rgb(20,22,35,0.90)), new Stop(1,Color.rgb(10,12,20,0.94))));
        hudBg.setStroke(Color.rgb(70,130,210,0.5)); hudBg.setStrokeWidth(1.5);
        hudBg.setLayoutX(16); hudBg.setLayoutY(16); hudBg.setEffect(new DropShadow(15,0,4,Color.rgb(0,0,0,0.6)));
        hudText = new Text();
        hudText.setFont(Font.font("Consolas", 11)); hudText.setFill(Color.rgb(235,240,250));
        hudText.setStroke(Color.rgb(0,0,0,0.4)); hudText.setStrokeWidth(0.4);
        hudText.setLayoutX(26); hudText.setLayoutY(32);
        Pane hudContainer = new Pane(hudBg, hudText);
        hudContainer.setPickOnBounds(false); hudContainer.setMouseTransparent(true);
        rootPane.getChildren().add(hudContainer); updateHUDText();
    }

    private void startHUDUpdater() {
        Timeline t = new Timeline(new KeyFrame(Duration.millis(100), e -> updateHUDText()));
        t.setCycleCount(Timeline.INDEFINITE); t.play();
    }

    private void updateHUDText() {
        StringBuilder sb = new StringBuilder();
        sb.append("☀️ СОЛНЕЧНАЯ СИСТЕМА\nУничтожено: ").append(destroyedPlanets).append("\n\n");
        if (cometActive && cometGroup!=null) {
            double sp = Math.hypot(cometGroup.getTranslateX(),Math.hypot(cometGroup.getTranslateY(),cometGroup.getTranslateZ()))*10;
            double dist = Math.hypot(getTargetX()-cometGroup.getTranslateX(),Math.hypot(getTargetY()-cometGroup.getTranslateY(),getTargetZ()-cometGroup.getTranslateZ()));
            sb.append("☄️ КОМЕТА:\nСкорость: ").append(String.format("%.1f", sp)).append("\nДистанция: ").append(String.format("%.0f", dist)).append("\n\n");
        }
        if (blackHoleActive) sb.append("🕳 ЧЁРНАЯ ДЫРА: Активна\n\n");
        sb.append("УПРАВЛЕНИЕ:\nПКМ+Мышь - Вращение | WASD - Полёт | Q/E - Вверх/Вниз\n0-8 - Запустить комету | B - Чёрная дыра | ` - Консоль SQL\nПробел - Пауза | +/- - Скорость | X - Сброс");
        hudText.setText(sb.toString());
    }

    private String getTargetDisplayName() {
        if (targetIndex == -1) return "Солнце";
        if (targetIndex >= 1 && targetIndex <= 8) {
            String[] names = { " ", "Меркурий", "Венера", "Земля", "Марс", "Юпитер", "Сатурн", "Уран", "Нептун"};
            return names[targetIndex];
        }
        return "Неизвестно";
    }

    private double getTargetX() {
        if (targetIndex == -1) return 0;
        if (targetIndex >= 1 && targetIndex < planetSpheres.size()) return planetSpheres.get(targetIndex).getTranslateX();
        return 0;
    }
    private double getTargetY() {
        if (targetIndex == -1) return 0;
        if (targetIndex >= 1 && targetIndex < planetSpheres.size()) return planetSpheres.get(targetIndex).getTranslateY();
        return 0;
    }
    private double getTargetZ() {
        if (targetIndex == -1) return 0;
        if (targetIndex >= 1 && targetIndex < planetSpheres.size()) return planetSpheres.get(targetIndex).getTranslateZ();
        return 0;
    }

    private void createStarDust() {
        for(int i=0;i<800;i++){
            Sphere d=new Sphere(0.2+random.nextDouble()*0.5);
            d.setMaterial(new PhongMaterial(Color.rgb(200,220,255,0.4)));
            d.setTranslateX((random.nextDouble()-0.5)*8000); d.setTranslateY((random.nextDouble()-0.5)*6000); d.setTranslateZ((random.nextDouble()-0.5)*8000);
            root3D.getChildren().add(d); starDust.add(d);
        }
    }
    private void createAsteroidBelt() {
        asteroids.clear(); asteroidSpeeds.clear();
        for(int i=0;i<400;i++){
            double r=240+random.nextDouble()*50, a=random.nextDouble()*Math.PI*2, y=(random.nextDouble()-0.5)*18;
            Sphere ast=new Sphere(1+random.nextDouble()*3.5);
            ast.setMaterial(new PhongMaterial(Color.rgb(120+random.nextInt(60),100+random.nextInt(40),80+random.nextInt(30))));
            ast.setTranslateX(r*Math.cos(a)); ast.setTranslateY(y); ast.setTranslateZ(r*Math.sin(a));
            root3D.getChildren().add(ast); asteroids.add(ast); asteroidSpeeds.add(0.3+random.nextDouble()*0.4);
        }
    }
    private void updateAsteroids() {
        for(int i=0;i<asteroids.size();i++){
            Sphere a=asteroids.get(i);
            double x=a.getTranslateX(),z=a.getTranslateZ(), rad=Math.hypot(x,z), ang=Math.atan2(z,x);
            ang+=asteroidSpeeds.get(i)*timeSpeed*0.02;
            a.setTranslateX(rad*Math.cos(ang)); a.setTranslateZ(rad*Math.sin(ang)); a.setRotate(a.getRotate()+1.5);
        }
    }
    private void updateStarDust() {
        for(Sphere d:starDust){
            d.setTranslateX(d.getTranslateX()+(random.nextDouble()-0.5)*0.5);
            d.setTranslateY(d.getTranslateY()+(random.nextDouble()-0.5)*0.5);
            d.setTranslateZ(d.getTranslateZ()+(random.nextDouble()-0.5)*0.5);
            if(Math.abs(d.getTranslateX())>5000) d.setTranslateX((random.nextDouble()-0.5)*8000);
            if(Math.abs(d.getTranslateY())>4000) d.setTranslateY((random.nextDouble()-0.5)*6000);
            if(Math.abs(d.getTranslateZ())>5000) d.setTranslateZ((random.nextDouble()-0.5)*8000);
        }
    }
    private void printControls() {
        System.out.println("\n☀️ 3D СОЛНЕЧНАЯ СИСТЕМА\n-------------------------------\n0-8: Запустить комету | B: Чёрная дыра | `: Консоль SQL\nWASD: Полёт | ПКМ: Вращение | Пробел: Пауза | X: Сброс\n-------------------------------");
    }
    private void setupCamera() {
        camera=new PerspectiveCamera(true);
        camera.setNearClip(1); camera.setFarClip(100000);
        camera.setTranslateZ(2800);
        camera.getTransforms().addAll(cameraRotateY,cameraRotateX);
    }
    private void setupLights() {
        PointLight sunLight=new PointLight(Color.WHITE);
        sunLight.setMaxRange(40000);
        root3D.getChildren().addAll(sunLight, new AmbientLight(Color.gray(0.15)));
    }
    private void createSun() {
        Sphere sun=new Sphere(30);
        PhongMaterial mat=new PhongMaterial();
        try{ mat.setDiffuseMap(new Image(getClass().getResourceAsStream("/images/sun.jpg"))); mat.setSelfIlluminationMap(mat.getDiffuseMap()); }
        catch(Exception e){ mat.setDiffuseColor(Color.ORANGE.brighter()); }
        sun.setMaterial(mat); sun.setTranslateX(0); sun.setTranslateZ(0);
        root3D.getChildren().add(sun); planetSpheres.add(sun);
        planetRotations.add(new Rotate(0,Rotate.Y_AXIS)); sun.getTransforms().add(planetRotations.get(0));
    }
    private void createPlanets() {
        String[] tex={"mercury.jpg","venus.jpg","earth.jpg","mars.jpg","jupiter.jpg","saturn.jpg","uranus.jpg","neptune.jpg"};
        List<CelestialBody> bodies=solarSystem.getBodies();
        for(int i=1;i<bodies.size();i++){
            CelestialBody b=bodies.get(i);
            Sphere s=new Sphere(b.radius);
            PhongMaterial m=new PhongMaterial();
            try{ m.setDiffuseMap(new Image(getClass().getResourceAsStream("/images/"+tex[i-1]))); }
            catch(Exception e){ m.setDiffuseColor(b.color); }
            m.setSpecularColor(Color.WHITE); m.setSpecularPower(28);
            s.setMaterial(m); s.setTranslateX(b.x); s.setTranslateZ(b.z);
            root3D.getChildren().add(s); planetSpheres.add(s);
            Rotate r=new Rotate(0,Rotate.Y_AXIS); s.getTransforms().add(r); planetRotations.add(r);
        }
        createSaturnRings(); createEarthMoon();
    }
    private void createOrbits() {
        orbitLines.clear();
        List<CelestialBody> bodies=solarSystem.getBodies();
        for(int i=1;i<bodies.size();i++){
            double r=bodies.get(i).x; if(r<10) continue;
            OrbitLine ol=new OrbitLine(r);
            for(int d=0;d<360;d+=4){
                double rad=Math.toRadians(d);
                Sphere dot=new Sphere(0.8);
                dot.setMaterial(new PhongMaterial(Color.rgb(160,200,255,0.3)));
                dot.setTranslateX(r*Math.cos(rad)); dot.setTranslateZ(r*Math.sin(rad));
                root3D.getChildren().add(dot); ol.dots.add(dot);
            }
            orbitLines.add(ol);
        }
    }
    private void createStars() {
        for(int i=0;i<4500;i++){
            Sphere s=new Sphere(0.3+random.nextDouble()*2.5);
            Color c=switch(random.nextInt(7)){
                case 0,1->Color.WHITE; case 2->Color.rgb(220,240,255); case 3->Color.rgb(255,245,200);
                case 4->Color.rgb(255,210,190); case 5->Color.rgb(200,220,255); default->Color.rgb(240,240,255);
            };
            s.setMaterial(new PhongMaterial(c));
            s.setTranslateX((random.nextDouble()-0.5)*30000); s.setTranslateY((random.nextDouble()-0.5)*30000); s.setTranslateZ((random.nextDouble()-0.5)*30000);
            root3D.getChildren().add(s); stars.add(s);
        }
    }

    private void createBlackHoleParticleAt(double cx, double cy, double cz) {
        Sphere p = new Sphere(0.8 + random.nextDouble() * 1.5);
        p.setMaterial(new PhongMaterial(Color.rgb(200 + random.nextInt(55), 150 + random.nextInt(80), 100, 0.7)));
        p.setCullFace(CullFace.NONE);
        p.setEffect(new Glow(1.2));
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 80 + random.nextDouble() * 120;

        p.setTranslateX(cx + Math.cos(angle) * dist);
        p.setTranslateY(cy + (random.nextDouble() - 0.5) * 60);
        p.setTranslateZ(cz + Math.sin(angle) * dist);

        root3D.getChildren().add(p);
        blackHoleParticles.add(p);
    }

    private void launchComet(int target) {
        if(cometActive) return;
        targetIndex = target;
        cometGroup=new Group();
        cometNucleus=new Sphere(5); cometNucleus.setMaterial(new PhongMaterial(Color.rgb(180,80,40))); cometNucleus.setEffect(new Glow(1.2));
        cometInnerGlow=new Sphere(7.5); cometInnerGlow.setMaterial(new PhongMaterial(Color.rgb(255,100,30,0.6))); cometInnerGlow.setEffect(new Glow(1.5));
        cometOuterGlow=new Sphere(11); cometOuterGlow.setMaterial(new PhongMaterial(Color.rgb(255,70,20,0.3))); cometOuterGlow.setEffect(new Glow(2.0));
        cometGroup.getChildren().addAll(cometNucleus,cometInnerGlow,cometOuterGlow);
        cometTail=new ArrayList<>(); tailGlowParticles=new ArrayList<>();
        for(int i=0;i<35;i++){
            Box b=new Box(2.2,2.2,4.5); double t=1-i/35.0;
            b.setMaterial(new PhongMaterial(Color.rgb(255,(int)(100+155*t),(int)(20+60*t),Math.max(0.12,0.8-i*0.018))));
            b.setEffect(new Glow(1.4-i*0.03)); cometTail.add(b); cometGroup.getChildren().add(b);
            if(i%2==0){ Sphere g=new Sphere(1+random.nextDouble()*1.5); g.setMaterial(new PhongMaterial(Color.rgb(255,120+random.nextInt(80),30,0.5))); g.setEffect(new Glow(1.2)); tailGlowParticles.add(g); cometGroup.getChildren().add(g); }
        }
        if(cometLight!=null) root3D.getChildren().remove(cometLight); cometLight=new PointLight(); cometLight.setMaxRange(12000);
        if(cometFireLight!=null) root3D.getChildren().remove(cometFireLight); cometFireLight=new PointLight(); cometFireLight.setMaxRange(18000);
        root3D.getChildren().addAll(cometGroup,cometLight,cometFireLight);

        double sd = (target == 0) ? 8500 : 6500;
        double ang = random.nextDouble() * Math.PI * 2;
        double h = (random.nextDouble() - 0.5) * 2500;
        double sx, sy, sz;

        if(target == 0){ sx = Math.cos(ang) * sd; sy = h; sz = Math.sin(ang) * sd; System.out.println("Comet to SUN!"); }
        else {
            List<CelestialBody> bodies = solarSystem.getBodies();
            if (target < bodies.size()) {
                CelestialBody tg = bodies.get(target);
                sx = tg.x + Math.cos(ang) * sd; sy = tg.y + h; sz = tg.z + Math.sin(ang) * sd;
                System.out.println("Comet to: " + getTargetDisplayName());
            } else return;
        }
        cometGroup.setTranslateX(sx); cometGroup.setTranslateY(sy); cometGroup.setTranslateZ(sz);
        cometActive=true; cometTrailParticles.clear(); cometHeat=0.5; followYaw=0; followPitch=0; updateHUDText();
    }
    private void updateCometTail(double x,double y,double z,double vx,double vy,double vz) {
        if (cometTail==null) return; double sp=Math.hypot(vx,Math.hypot(vy,vz)); if(sp<0.1) return;
        double dx=-vx/sp,dy=-vy/sp,dz=-vz/sp,tl=5.5+sp/5+cometHeat*3;
        for(int i=0;i<cometTail.size();i++){
            Box f=cometTail.get(i); double dist=(i+1)*tl;
            f.setTranslateX(x+dx*dist); f.setTranslateY(y+dy*dist+Math.sin(i*0.5)*1.2); f.setTranslateZ(z+dz*dist);
            f.setRotate(Math.toDegrees(Math.atan2(dz,dx))); f.setRotationAxis(Rotate.Y_AXIS);
            double sc=(0.6+i/(double)cometTail.size()*1.4)*(1+cometHeat*0.5);
            f.setWidth(2.2*sc); f.setHeight(2.2*sc); f.setDepth(4.5*(0.7+i*0.03));
        }
        for(int i=0;i<tailGlowParticles.size();i++){
            Sphere g=tailGlowParticles.get(i); double off=(i+1)*(4.2+cometHeat*2);
            g.setTranslateX(x+dx*off+dz*Math.sin(i*0.8)*2.5); g.setTranslateY(y+dy*off+Math.cos(i*0.6)*1.8); g.setTranslateZ(z+dz*off-dx*Math.sin(i*0.8)*2.5);
            g.setEffect(new Glow(0.8+Math.sin(System.currentTimeMillis()*0.008+i)*0.4+cometHeat*0.5));
        }
    }
    private void explodePlanet(int idx) {
        if(idx < 1 || idx >= planetSpheres.size()) return;
        Sphere pl = planetSpheres.get(idx); CelestialBody bd = solarSystem.getBodies().get(idx);
        double px = pl.getTranslateX(), py = pl.getTranslateY(), pz = pl.getTranslateZ();
        bd.isDestroyed = true; activeImpulses.add(new GravitationalImpulse(px, py, pz, bd.radius * 12, bd.radius * 8));
        Sphere flash = new Sphere(bd.radius * 2); flash.setMaterial(new PhongMaterial(Color.rgb(255, 200, 100, 0.8))); flash.setEffect(new Glow(2.0));
        flash.setTranslateX(px); flash.setTranslateY(py); flash.setTranslateZ(pz); root3D.getChildren().add(flash);
        new Timeline(new KeyFrame(Duration.seconds(0), e->{flash.setScaleX(1);flash.setScaleY(1);flash.setScaleZ(1);}),
                new KeyFrame(Duration.seconds(0.4), e->{flash.setScaleX(2.5);flash.setScaleY(2.5);flash.setScaleZ(2.5);}),
                new KeyFrame(Duration.seconds(1.2), e->root3D.getChildren().remove(flash))).play();
        for(int i=0; i<150; i++){
            Sphere fr = new Sphere(0.3 + random.nextDouble() * bd.radius * 0.8); fr.setMaterial(new PhongMaterial(bd.color));
            fr.setTranslateX(px + (random.nextDouble()-0.5) * bd.radius * 2); fr.setTranslateY(py + (random.nextDouble()-0.5) * bd.radius * 2); fr.setTranslateZ(pz + (random.nextDouble()-0.5) * bd.radius * 2);
            root3D.getChildren().add(fr); double sp = 15 + random.nextDouble() * 40, a1 = random.nextDouble()*Math.PI*2, a2 = random.nextDouble()*Math.PI;
            activeFragments.add(new Fragment(fr, bd.vx + Math.sin(a1)*Math.cos(a2)*sp, bd.vy + Math.sin(a1)*Math.sin(a2)*sp*0.5, bd.vz + Math.cos(a1)*sp));
        }
        clearCometTrail(); updateHUDText();
    }
    private void explodeSun() {
        Sphere flash = new Sphere(600); flash.setMaterial(new PhongMaterial(Color.WHITE)); flash.setEffect(new Glow(1.2)); root3D.getChildren().add(flash);
        CelestialBody sun = solarSystem.getBodies().get(0); sun.isDestroyed = true;
        for(int i=0; i<1500; i++){
            Sphere fr = new Sphere(1.5 + random.nextDouble()*15); double t = (double)i/1500;
            fr.setMaterial(new PhongMaterial(Color.rgb(255, Math.max(0, (int)(200 * (1 - t * 1.2))), Math.max(0, (int)(80 * (1 - t * 1.8))), Math.max(0.0, 1.0 - t * 0.6))));
            double a1 = random.nextDouble()*Math.PI*2, a2 = random.nextDouble()*Math.PI, d = 200 + random.nextDouble()*800;
            fr.setTranslateX(Math.sin(a1)*Math.cos(a2)*d); fr.setTranslateY(Math.sin(a1)*Math.sin(a2)*d*0.7); fr.setTranslateZ(Math.cos(a1)*d);
            root3D.getChildren().add(fr); double sp = 35 + random.nextDouble()*80;
            activeFragments.add(new Fragment(fr, Math.sin(a1)*Math.cos(a2)*sp, Math.sin(a1)*Math.sin(a2)*sp*0.7, Math.cos(a1)*sp));
        }
        System.out.println("Sun exploded!");
        new Timeline(new KeyFrame(Duration.seconds(0.7), e->{flash.setScaleX(5);flash.setScaleY(5);flash.setScaleZ(5);}),
                new KeyFrame(Duration.seconds(4), e->root3D.getChildren().remove(flash))).play();
        clearCometTrail(); updateHUDText();
    }
    private void clearCometTrail() {
        if(cometLight!=null){root3D.getChildren().remove(cometLight); cometLight=null;}
        if(cometFireLight!=null){root3D.getChildren().remove(cometFireLight); cometFireLight=null;}
        for(Sphere t:cometTrailParticles) root3D.getChildren().remove(t); cometTrailParticles.clear(); cometActive=false;
        if(cometGroup!=null){root3D.getChildren().remove(cometGroup); cometGroup=null;}
    }
    private void resetSystem() {
        root3D.getChildren().clear(); activeFragments.clear(); activeImpulses.clear(); planetSpheres.clear(); orbitLines.clear();
        asteroids.clear(); starDust.clear(); destroyedPlanets=0; clearCometTrail();
        if(blackHoleGroup!=null){root3D.getChildren().remove(blackHoleGroup); blackHoleGroup=null;}
        for(Sphere p:new ArrayList<>(blackHoleParticles)) root3D.getChildren().remove(p); blackHoleParticles.clear();
        blackHoleActive = false;
        processedCommandIds.clear();
        solarSystem=new SolarSystem(); setupLights(); createSun(); createPlanets(); createOrbits(); createStars(); createStarDust(); createAsteroidBelt(); updateHUDText();
    }
    private void startGameLoop() {
        new AnimationTimer() {
            double lastTime = 0;
            @Override public void handle(long now) {
                if(lastTime==0) lastTime=now;
                double frameDt = Math.min((now-lastTime)/1_000_000_000.0, 0.1); lastTime = now;
                updateCameraFollow();
                if(isRunning) {
                    physicsTimeAcc += frameDt * timeSpeed;
                    while(physicsTimeAcc >= PHYSICS_DT) { solarSystem.stepPhysics(PHYSICS_DT); syncPhysicsToVisuals(); physicsTimeAcc -= PHYSICS_DT; }
                    updateAsteroids(); updateStarDust(); updateComet(); updateFragments(); updateImpulses();
                }
            }
        }.start();
    }
    private void syncPhysicsToVisuals() {
        List<CelestialBody> bodies = solarSystem.getBodies();
        for (int i = planetSpheres.size() - 1; i >= 0; i--) {
            if (i >= bodies.size() || bodies.get(i).isDestroyed) {
                root3D.getChildren().remove(planetSpheres.get(i));
                if (i < planetRotations.size()) planetRotations.remove(i); planetSpheres.remove(i);
                int orbitIdx = i - 1;
                if(orbitIdx >= 0 && orbitIdx < orbitLines.size() && orbitLines.get(orbitIdx) != null){
                    for(Sphere d : orbitLines.get(orbitIdx).dots) root3D.getChildren().remove(d); orbitLines.remove(orbitIdx);
                }
                if (i < bodies.size()) bodies.remove(i); destroyedPlanets++; updateHUDText();
            } else {
                CelestialBody b = bodies.get(i);
                planetSpheres.get(i).setTranslateX(b.x); planetSpheres.get(i).setTranslateY(b.y); planetSpheres.get(i).setTranslateZ(b.z);
                if(i < planetRotations.size()) planetRotations.get(i).setAngle(planetRotations.get(i).getAngle() + 3 * timeSpeed);
            }
        }
        if(saturnRings != null && bodies.size() > 5 && !bodies.get(5).isDestroyed) {
            saturnRings.setTranslateX(bodies.get(5).x); saturnRings.setTranslateY(bodies.get(5).y); saturnRings.setTranslateZ(bodies.get(5).z);
        }
        if(earthMoon != null && bodies.size() > 2 && !bodies.get(2).isDestroyed) {
            CelestialBody earth = bodies.get(2); double ma = System.currentTimeMillis() * 0.0008, md = 28;
            earthMoon.setTranslateX(earth.x + md * Math.cos(ma)); earthMoon.setTranslateY(earth.y + 2 * Math.sin(ma * 1.3)); earthMoon.setTranslateZ(earth.z + md * Math.sin(ma));
        }
        if(blackHoleGroup != null) {
            CelestialBody bh = null;
            for(CelestialBody b : bodies) { if(b.isBlackHole && !b.isDestroyed) { bh = b; break; } }
            if(bh != null) {
                blackHoleGroup.setTranslateX(bh.x); blackHoleGroup.setTranslateY(bh.y); blackHoleGroup.setTranslateZ(bh.z);
                accretionDisk.setRotate(accretionDisk.getRotate() + 2.5 * timeSpeed);
                List<Sphere> toRemove = new ArrayList<>(); int toCreate = 0;
                for (Sphere p : blackHoleParticles) {
                    double dx = bh.x - p.getTranslateX(), dy = bh.y - p.getTranslateY(), dz = bh.z - p.getTranslateZ();
                    double dist = Math.hypot(dx, Math.hypot(dy, dz));
                    if (dist < bh.radius * 1.5) { toRemove.add(p); toCreate++; }
                    else { double f = bh.mass * 0.005 / (dist * dist + 50); p.setTranslateX(p.getTranslateX() + dx / dist * f * timeSpeed); p.setTranslateY(p.getTranslateY() + dy / dist * f * 0.3 * timeSpeed); p.setTranslateZ(p.getTranslateZ() + dz / dist * f * timeSpeed); p.setRotate(p.getRotate() + 4); }
                }
                for (Sphere p : toRemove) { root3D.getChildren().remove(p); blackHoleParticles.remove(p); }
                for (int k = 0; k < toCreate; k++) createBlackHoleParticleAt(bh.x, bh.y, bh.z);
            } else {
                blackHoleActive = false; root3D.getChildren().remove(blackHoleGroup); blackHoleGroup = null;
                for(Sphere p : new ArrayList<>(blackHoleParticles)) root3D.getChildren().remove(p); blackHoleParticles.clear(); updateHUDText();
            }
        }
    }
    private void updateComet() {
        if(!cometActive || cometGroup==null) return;
        cometNucleus.setRotate(cometNucleus.getRotate()+3); cometInnerGlow.setRotate(cometInnerGlow.getRotate()+1.5);
        double tx=0,ty=0,tz=0; double cr = (targetIndex == 0) ? 100 : 60;
        if(targetIndex == 0){ tx=0;ty=0;tz=0; }
        else if(targetIndex >= 1 && targetIndex < planetSpheres.size()){
            Sphere tg = planetSpheres.get(targetIndex); tx = tg.getTranslateX(); ty = tg.getTranslateY(); tz = tg.getTranslateZ();
            cr = solarSystem.getBodies().get(targetIndex).radius + 25;
        } else { cometActive=false; return; }
        double cx = cometGroup.getTranslateX(), cy = cometGroup.getTranslateY(), cz = cometGroup.getTranslateZ();
        double dx = tx-cx, dy = ty-cy, dz = tz-cz, dist = Math.hypot(dx, Math.hypot(dy,dz));
        if(dist < cr){ root3D.getChildren().remove(cometGroup); clearCometTrail(); if(targetIndex == 0) explodeSun(); else explodePlanet(targetIndex); return; }
        double bs = (targetIndex == 0) ? 9 : 11.5; double sf = Math.min(1, dist/2500);
        double sp = bs * (0.5 + sf * 0.5) + cometHeat * 1.5;
        double vx = dx/dist * sp, vy = dy/dist * sp, vz = dz/dist * sp;
        cometGroup.setTranslateX(cx + vx); cometGroup.setTranslateY(cy + vy); cometGroup.setTranslateZ(cz + vz);
        if(cometLight != null){ cometLight.setTranslateX(cx + vx); cometLight.setTranslateY(cy + vy); cometLight.setTranslateZ(cz + vz); }
        if(cometFireLight != null){ cometFireLight.setTranslateX(cx + vx); cometFireLight.setTranslateY(cy + vy); cometFireLight.setTranslateZ(cz + vz); }
        updateCometTail(cx+vx, cy+vy, cz+vz, vx, vy, vz); double temp = Math.min(1, 1 - dist/2000); cometHeat = temp;
    }
    private void updateImpulses() {
        Iterator<GravitationalImpulse> impIt=activeImpulses.iterator();
        while(impIt.hasNext()){
            GravitationalImpulse imp=impIt.next(); imp.life-=0.02;
            if(imp.life <=0){ impIt.remove(); continue; }
            for(int i=0;i<planetSpheres.size();i++){
                Sphere pl=planetSpheres.get(i);
                double dx=imp.x-pl.getTranslateX(), dy=imp.y-pl.getTranslateY(), dz=imp.z-pl.getTranslateZ(), dist=Math.hypot(dx,Math.hypot(dy,dz));
                if(dist<imp.radius){
                    double force=imp.strength*(1-dist/imp.radius)*imp.life/(dist+5)*timeSpeed*1.5;
                    pl.setTranslateX(pl.getTranslateX()+dx/dist*force*0.5);
                    pl.setTranslateY(pl.getTranslateY()+dy/dist*force*0.2);
                    pl.setTranslateZ(pl.getTranslateZ()+dz/dist*force*0.5);
                }
            }
        }
    }
    private void updateFragments(){
        Iterator<Fragment> it=activeFragments.iterator();
        while(it.hasNext()){
            Fragment f=it.next();
            f.sphere.setTranslateX(f.sphere.getTranslateX()+f.vx*timeSpeed);
            f.sphere.setTranslateY(f.sphere.getTranslateY()+f.vy*timeSpeed);
            f.sphere.setTranslateZ(f.sphere.getTranslateZ()+f.vz*timeSpeed);
            f.sphere.setRotate(f.sphere.getRotate()+5);
            f.vx*=0.995; f.vy*=0.995; f.vz*=0.995; f.life-=0.001*timeSpeed;
            if(f.life <=0){ root3D.getChildren().remove(f.sphere); it.remove(); }
        }
    }
    private void setupControls() {
        scene.setOnMousePressed(e->{ if(e.isSecondaryButtonDown()){ rightMousePressed=true; mouseOldX=e.getSceneX(); mouseOldY=e.getSceneY(); } });
        scene.setOnMouseReleased(e->rightMousePressed=false);
        scene.setOnMouseDragged(e->{
            if(rightMousePressed){
                double dx=e.getSceneX()-mouseOldX,dy=e.getSceneY()-mouseOldY;
                if(cameraMode==CameraMode.FOLLOW_COMET && cometActive){ followYaw+=dx*0.5; followPitch+=dy*0.3; followPitch=Math.max(-30,Math.min(30,followPitch)); }
                else if(cameraMode==CameraMode.FREE){ cameraRotateY.setAngle(cameraRotateY.getAngle()-dx*0.4); cameraRotateX.setAngle(cameraRotateX.getAngle()-dy*0.4); cameraRotateX.setAngle(Math.max(-89,Math.min(89,cameraRotateX.getAngle()))); }
                mouseOldX=e.getSceneX(); mouseOldY=e.getSceneY();
            }
        });
        scene.setOnKeyPressed(e->{
            if (e.getCode() == KeyCode.BACK_QUOTE) {
                consoleVisible = !consoleVisible; consoleInput.setVisible(consoleVisible); consoleInput.setEditable(consoleVisible);
                if (consoleVisible) consoleInput.requestFocus(); else rootPane.requestFocus(); return;
            }
            switch(e.getCode()){
                case SPACE: isRunning=!isRunning; System.out.println(isRunning?"Running":"Paused"); break;
                case ADD: case EQUALS: timeSpeed*=1.5; timeSpeed=Math.min(timeSpeed,8.0); break;
                case SUBTRACT: case MINUS: timeSpeed/=1.5; timeSpeed=Math.max(timeSpeed,0.1); break;
                case R: camera.setTranslateX(0);camera.setTranslateY(0);camera.setTranslateZ(2800);cameraRotateY.setAngle(180);cameraRotateX.setAngle(0);followYaw=0;followPitch=0;cameraMode=CameraMode.FREE; break;
                case C: if(cometActive) cameraMode=CameraMode.FOLLOW_COMET; break; // 🔥 СЛЕЖЕНИЕ ЗА КОМЕТОЙ
                case F: cameraMode=CameraMode.FREE; break;
                case B: spawnBlackHole(); break;
                case X: resetSystem(); break;
                case DIGIT0: launchComet(0); break; case DIGIT1: launchComet(1); break; case DIGIT2: launchComet(2); break;
                case DIGIT3: launchComet(3); break; case DIGIT4: launchComet(4); break; case DIGIT5: launchComet(5); break;
                case DIGIT6: launchComet(6); break; case DIGIT7: launchComet(7); break; case DIGIT8: launchComet(8); break;
                case W: wPressed=true; break; case A: aPressed=true; break; case S: sPressed=true; break; case D: dPressed=true; break;
                case Q: qPressed=true; break; case E: ePressed=true; break; default: break;
            }
        });
        scene.setOnKeyReleased(e->{ switch(e.getCode()){ case W: wPressed=false; break; case A: aPressed=false; break; case S: sPressed=false; break; case D: dPressed=false; break; case Q: qPressed=false; break; case E: ePressed=false; break; default: break; } });
    }
    private void startMovementTimer() {
        new AnimationTimer() {
            @Override public void handle(long now) {
                if (cameraMode==CameraMode.FREE){
                    double sp=moveSpeed,ay=Math.toRadians(cameraRotateY.getAngle()),ax=Math.toRadians(cameraRotateX.getAngle()),mx=0,my=0,mz=0;
                    if(wPressed){ mx+=Math.sin(ay)*Math.cos(ax)*sp; my+=Math.sin(ax)*sp; mz+=Math.cos(ay)*Math.cos(ax)*sp; }
                    if(sPressed){ mx-=Math.sin(ay)*Math.cos(ax)*sp; my-=Math.sin(ax)*sp; mz-=Math.cos(ay)*Math.cos(ax)*sp; }
                    if(aPressed){ mx-=Math.cos(ay)*sp; mz+=Math.sin(ay)*sp; }
                    if(dPressed){ mx+=Math.cos(ay)*sp; mz-=Math.sin(ay)*sp; }
                    if(qPressed) my-=sp; if(ePressed) my+=sp;
                    camera.setTranslateX(camera.getTranslateX()+mx); camera.setTranslateY(camera.getTranslateY()+my); camera.setTranslateZ(camera.getTranslateZ()+mz);
                }
            }
        }.start();
    }
    private void updateCameraFollow() {
        if(cameraMode==CameraMode.FOLLOW_COMET && cometActive && cometGroup!=null){
            double cx=cometGroup.getTranslateX(),cy=cometGroup.getTranslateY(),cz=cometGroup.getTranslateZ();
            double dirX=0,dirZ=-1;
            if(targetIndex>=1 && targetIndex<planetSpheres.size()){
                Sphere tg=planetSpheres.get(targetIndex); double dx=tg.getTranslateX()-cx,dz=tg.getTranslateZ()-cz,len=Math.hypot(dx,dz);
                if(len>0.01){ dirX=dx/len; dirZ=dz/len; }
            } else if(targetIndex==0){ double dx=-cx,dz=-cz,len=Math.hypot(dx,dz); if(len>0.01){ dirX=dx/len; dirZ=dz/len; } }
            camera.setTranslateX(cx-dirX*followDistance); camera.setTranslateY(cy+followHeight); camera.setTranslateZ(cz-dirZ*followDistance);
            double ang= Math.toDegrees(Math.atan2(cx-camera.getTranslateX(),cz-camera.getTranslateZ()));
            cameraRotateY.setAngle(ang+followYaw); cameraRotateX.setAngle(followPitch);
        }
    }
    private void createSaturnRings() {
        if(planetSpheres.size() <=5) return; saturnRings=new Group();
        double[] rr={24,29,35},rw={4.5,3.2,2.8};
        for(int i=0;i<rr.length;i++){
            Cylinder c=new Cylinder(rr[i],rw[i]); c.setRotationAxis(Rotate.X_AXIS); c.setRotate(82);
            c.setMaterial(new PhongMaterial(Color.rgb(230,210,170,0.65+i*0.1))); saturnRings.getChildren().add(c);
        }
        root3D.getChildren().add(saturnRings);
    }
    private void createEarthMoon() { earthMoon=new Sphere(3.8); earthMoon.setMaterial(new PhongMaterial(Color.LIGHTGRAY)); root3D.getChildren().add(earthMoon); }

    private void spawnBlackHole() {
        if(blackHoleActive) {
            if(blackHoleGroup != null) { root3D.getChildren().remove(blackHoleGroup); blackHoleGroup = null; }
            for(Sphere p : new ArrayList<>(blackHoleParticles)) root3D.getChildren().remove(p);
            blackHoleParticles.clear();
            blackHoleActive = false;
        }

        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 200 + random.nextDouble() * 150;
        blackHoleX = dist * Math.cos(angle);
        blackHoleY = (random.nextDouble() - 0.5) * 50;
        blackHoleZ = dist * Math.sin(angle);

        CelestialBody bh = new CelestialBody("BlackHole", 25, 25000.0, Color.BLACK, blackHoleX, blackHoleZ);
        bh.y = blackHoleY; bh.isBlackHole = true;
        solarSystem.addBody(bh);

        spawnBlackHoleVisual(bh);

        if(observers != null && !observers.isEmpty()) {
            Map<String, Object> ev = new HashMap<>(); ev.put("name", "BlackHole");
            notifyObservers("BLACK_HOLE_SPAWNED", ev);
        }

        blackHoleActive = true;
        updateHUDText();
        System.out.println("🕳 Black hole spawned at: (" + blackHoleX + ", " + blackHoleY + ", " + blackHoleZ + ")");
    }

    public static void main(String[] args) { launch(args); }
}