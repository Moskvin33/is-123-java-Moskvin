package solarsystem;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.*;
import javafx.scene.effect.*;
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
import java.util.*;

public class Main3D extends Application {
    private Group root3D; private Pane rootPane; private Scene scene;
    private PerspectiveCamera camera; private SolarSystem solarSystem;
    private double timeSpeed = 0.8; private boolean isRunning = true;
    private List<Sphere> planetSpheres = new ArrayList<>();
    private List<Sphere> stars = new ArrayList<>();
    private List<OrbitLine> orbitLines = new ArrayList<>();
    private Random random = new Random();
    private Group cometGroup; private Sphere cometNucleus, cometInnerGlow, cometOuterGlow;
    private List<Box> cometTail; private List<Sphere> tailGlowParticles;
    private boolean cometActive = false; private int targetIndex = -2;
    private double cometHeat = 0.5; private List<Sphere> cometTrailParticles = new ArrayList<>();

    private List<Sphere> asteroids = new ArrayList<>();
    private List<Double> asteroidSpeeds = new ArrayList<>();
    private List<Sphere> starDust = new ArrayList<>();

    private Text hudText; private Rectangle hudBg; private int destroyedPlanets = 0;
    private Group blackHoleGroup; private Sphere blackHoleSphere, blackHoleGlow;
    private Cylinder accretionDisk; private PointLight blackHoleLight;
    private boolean blackHoleActive = false;
    private double blackHoleX, blackHoleY, blackHoleZ;
    private List<Sphere> blackHoleParticles = new ArrayList<>();

    private enum CameraMode { FREE, FOLLOW_COMET }
    private CameraMode cameraMode = CameraMode.FREE;
    private double followDistance = 300, followHeight = 0, followYaw = 0, followPitch = 0;
    private boolean wPressed, aPressed, sPressed, dPressed, qPressed, ePressed;
    private boolean rightMousePressed; private double moveSpeed = 12.0;
    private final Rotate cameraRotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate cameraRotateY = new Rotate(180, Rotate.Y_AXIS);
    private double mouseOldX, mouseOldY;
    private PointLight cometLight, cometFireLight;
    private List<Rotate> planetRotations = new ArrayList<>();
    private Group saturnRings; private Sphere earthMoon;
    private List<GravitationalImpulse> activeImpulses = new ArrayList<>();
    private List<Fragment> activeFragments = new ArrayList<>();

    private double physicsTimeAcc = 0;
    private final double PHYSICS_DT = 0.016;

    private class OrbitLine { List<Sphere> dots = new ArrayList<>(); double radius; OrbitLine(double r){ this.radius=r; } }
    private class Fragment { Sphere sphere; double vx,vy,vz,life; Fragment(Sphere s,double vx,double vy,double vz){ this.sphere=s; this.vx=vx; this.vy=vy; this.vz=vz; this.life=1.0; } }
    private class GravitationalImpulse { double x,y,z,strength,radius,life; GravitationalImpulse(double x,double y,double z,double s,double r){ this.x=x;this.y=y;this.z=z;this.strength=s;this.radius=r;this.life=3.0; } }

    @Override
    public void start(Stage primaryStage) {
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
        setupHUD(); setupControls();
        startGameLoop(); startMovementTimer(); startHUDUpdater();
        primaryStage.setTitle("3D Солнечная система");
        primaryStage.setScene(scene); primaryStage.show();
        printControls();
    }

    private void setupHUD() {
        hudBg = new Rectangle(320, 420);
        hudBg.setArcWidth(12); hudBg.setArcHeight(12);
        hudBg.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE, new Stop(0,Color.rgb(20,22,35,0.90)), new Stop(1,Color.rgb(10,12,20,0.94))));
        hudBg.setStroke(Color.rgb(70,130,210,0.5)); hudBg.setStrokeWidth(1.5);
        hudBg.setLayoutX(16); hudBg.setLayoutY(16); hudBg.setEffect(new DropShadow(15,0,4,Color.rgb(0,0,0,0.6)));
        hudText = new Text();
        hudText.setFont(Font.font("Consolas", 11)); hudText.setFill(Color.rgb(235,240,250));
        hudText.setStroke(Color.rgb(0,0,0,0.4)); hudText.setStrokeWidth(0.4);
        hudText.setLayoutX(26); hudText.setLayoutY(32);
        Pane hudContainer = new Pane(hudBg, hudText); hudContainer.setPickOnBounds(false); hudContainer.setMouseTransparent(true);
        rootPane.getChildren().add(hudContainer); updateHUDText();
    }

    private void startHUDUpdater() {
        Timeline t = new Timeline(new KeyFrame(Duration.millis(100), new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) { updateHUDText(); }
        }));
        t.setCycleCount(Timeline.INDEFINITE); t.play();
    }

    private void updateHUDText() {
        StringBuilder sb = new StringBuilder();
        sb.append("ОГНЕННАЯ КОМЕТА\n Уничтожено: ").append(destroyedPlanets).append("\n\n");
        if (cometActive && cometGroup!=null) {
            double sp = Math.hypot(cometGroup.getTranslateX(),Math.hypot(cometGroup.getTranslateY(),cometGroup.getTranslateZ()))*10;
            double dist = Math.hypot(getTargetX()-cometGroup.getTranslateX(),Math.hypot(getTargetY()-cometGroup.getTranslateY(),getTargetZ()-cometGroup.getTranslateZ()));
            double temp = 500 + cometHeat * 2000;
            String targetName = getTargetDisplayName();
            sb.append("КОМЕТА:\n").append(String.format("Скорость: %.1f\n Дистанция: %.0f\n Температура: %.0f C\n Цель: %s\n\n", sp, dist, temp, targetName));
        }
        if (blackHoleActive) sb.append("ЧЕРНАЯ ДЫРА:\n Притяжение: Активно\n\n");
        sb.append("УПРАВЛЕНИЕ:\n ПКМ+мышь - вращение\n WASD - движение | Q/E - вверх/вниз\n 0 - СОЛНЦЕ | 1-8 - ПЛАНЕТЫ\n C/F - камера за кометой/свободная\n B - чёрная дыра | Пробел - пауза\n +/- - скорость | R - сброс | X - рестарт");
        hudText.setText(sb.toString());
    }

    private String getTargetDisplayName() {
        if (targetIndex == -1) return "СОЛНЦЕ";
        if (targetIndex >= 1 && targetIndex <= 8) {
            String[] names = {"", "Меркурий", "Венера", "Земля", "Марс", "Юпитер", "Сатурн", "Уран", "Нептун"};
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

    private void createStarDust() { for(int i=0;i<800;i++){ Sphere d=new Sphere(0.2+random.nextDouble()*0.5); d.setMaterial(new PhongMaterial(Color.rgb(200,220,255,0.4))); d.setTranslateX((random.nextDouble()-0.5)*8000); d.setTranslateY((random.nextDouble()-0.5)*6000); d.setTranslateZ((random.nextDouble()-0.5)*8000); root3D.getChildren().add(d); starDust.add(d); } }
    private void createAsteroidBelt() { asteroids.clear(); asteroidSpeeds.clear(); for(int i=0;i<400;i++){ double r=240+random.nextDouble()*50, a=random.nextDouble()*Math.PI*2, y=(random.nextDouble()-0.5)*18; Sphere ast=new Sphere(1+random.nextDouble()*3.5); ast.setMaterial(new PhongMaterial(Color.rgb(120+random.nextInt(60),100+random.nextInt(40),80+random.nextInt(30)))); ast.setTranslateX(r*Math.cos(a)); ast.setTranslateY(y); ast.setTranslateZ(r*Math.sin(a)); root3D.getChildren().add(ast); asteroids.add(ast); asteroidSpeeds.add(0.3+random.nextDouble()*0.4); } }
    private void updateAsteroids() { for(int i=0;i<asteroids.size();i++){ Sphere a=asteroids.get(i); double x=a.getTranslateX(),z=a.getTranslateZ(), rad=Math.hypot(x,z), ang=Math.atan2(z,x); ang+=asteroidSpeeds.get(i)*timeSpeed*0.02; a.setTranslateX(rad*Math.cos(ang)); a.setTranslateZ(rad*Math.sin(ang)); a.setRotate(a.getRotate()+1.5); } }
    private void updateStarDust() { for(Sphere d:starDust){ d.setTranslateX(d.getTranslateX()+(random.nextDouble()-0.5)*0.5); d.setTranslateY(d.getTranslateY()+(random.nextDouble()-0.5)*0.5); d.setTranslateZ(d.getTranslateZ()+(random.nextDouble()-0.5)*0.5); if(Math.abs(d.getTranslateX())>5000) d.setTranslateX((random.nextDouble()-0.5)*8000); if(Math.abs(d.getTranslateY())>4000) d.setTranslateY((random.nextDouble()-0.5)*6000); if(Math.abs(d.getTranslateZ())>5000) d.setTranslateZ((random.nextDouble()-0.5)*8000); } }
    private void printControls() { System.out.println("\n3D СОЛНЕЧНАЯ СИСТЕМА (N-Body Physics)\n-------------------------------\n 0 - СОЛНЦЕ | 1-8 - ПЛАНЕТЫ\n WASD/QE - движение | ПКМ - вращение\n B - чёрная дыра | C/F - камера\n Пробел - пауза | +/- - время | X - рестарт\n-------------------------------"); }
    private void setupCamera() { camera=new PerspectiveCamera(true); camera.setNearClip(1); camera.setFarClip(100000); camera.setTranslateZ(2800); camera.getTransforms().addAll(cameraRotateY,cameraRotateX); }
    private void setupLights() { PointLight sunLight=new PointLight(Color.WHITE); sunLight.setMaxRange(40000); root3D.getChildren().addAll(sunLight, new AmbientLight(Color.gray(0.15))); }

    private void createSun() { Sphere sun=new Sphere(30); PhongMaterial mat=new PhongMaterial(); try{ mat.setDiffuseMap(new Image(getClass().getResourceAsStream("/images/sun.jpg"))); mat.setSelfIlluminationMap(mat.getDiffuseMap()); } catch(Exception e){ mat.setDiffuseColor(Color.ORANGE.brighter()); } sun.setMaterial(mat); sun.setTranslateX(0); sun.setTranslateZ(0); root3D.getChildren().add(sun); planetSpheres.add(sun); planetRotations.add(new Rotate(0,Rotate.Y_AXIS)); sun.getTransforms().add(planetRotations.get(0)); }
    private void createPlanets() { String[] tex={"mercury.jpg", "venus.jpg", "earth.jpg", "mars.jpg", "jupiter.jpg", "saturn.jpg", "uranus.jpg", "neptune.jpg"}; List<CelestialBody> bodies=solarSystem.getBodies(); for(int i=1;i<bodies.size();i++){ CelestialBody b=bodies.get(i); Sphere s=new Sphere(b.radius); PhongMaterial m=new PhongMaterial(); try{ m.setDiffuseMap(new Image(getClass().getResourceAsStream("/images/"+tex[i-1]))); } catch(Exception e){ m.setDiffuseColor(b.color); } m.setSpecularColor(Color.WHITE); m.setSpecularPower(28); s.setMaterial(m); s.setTranslateX(b.x); s.setTranslateZ(b.z); root3D.getChildren().add(s); planetSpheres.add(s); Rotate r=new Rotate(0,Rotate.Y_AXIS); s.getTransforms().add(r); planetRotations.add(r); } createSaturnRings(); createEarthMoon(); }
    private void createOrbits() { orbitLines.clear(); List<CelestialBody> bodies=solarSystem.getBodies(); for(int i=1;i<bodies.size();i++){ double r=bodies.get(i).x; if(r<10) continue; OrbitLine ol=new OrbitLine(r); for(int d=0;d<360;d+=4){ double rad=Math.toRadians(d); Sphere dot=new Sphere(0.8); dot.setMaterial(new PhongMaterial(Color.rgb(160,200,255,0.3))); dot.setTranslateX(r*Math.cos(rad)); dot.setTranslateZ(r*Math.sin(rad)); root3D.getChildren().add(dot); ol.dots.add(dot); } orbitLines.add(ol); } }
    private void createStars() { for(int i=0;i<4500;i++){ Sphere s=new Sphere(0.3+random.nextDouble()*2.5); Color c=switch(random.nextInt(7)){ case 0,1->Color.WHITE; case 2->Color.rgb(220,240,255); case 3->Color.rgb(255,245,200); case 4->Color.rgb(255,210,190); case 5->Color.rgb(200,220,255); default->Color.rgb(240,240,255); }; s.setMaterial(new PhongMaterial(c)); s.setTranslateX((random.nextDouble()-0.5)*30000); s.setTranslateY((random.nextDouble()-0.5)*30000); s.setTranslateZ((random.nextDouble()-0.5)*30000); root3D.getChildren().add(s); stars.add(s); } }

    private void spawnBlackHole() { if(blackHoleActive) return; blackHoleActive=true; double angle=random.nextDouble()*Math.PI*2, dist=350+random.nextDouble()*200; blackHoleX=dist*Math.cos(angle); blackHoleY=(random.nextDouble()-0.5)*100; blackHoleZ=dist*Math.sin(angle); CelestialBody bh=new CelestialBody("BlackHole", 25, 25000.0, Color.BLACK, blackHoleX, blackHoleZ); bh.y=blackHoleY; bh.isBlackHole=true; solarSystem.addBody(bh); blackHoleGroup=new Group(); blackHoleSphere=new Sphere(25); blackHoleSphere.setMaterial(new PhongMaterial(Color.rgb(5,5,10,0.98))); blackHoleGlow=new Sphere(55); blackHoleGlow.setMaterial(new PhongMaterial(Color.rgb(80,40,120,0.15))); blackHoleGlow.setEffect(new Bloom(0.8)); accretionDisk=new Cylinder(3, 80); accretionDisk.setRotationAxis(Rotate.X_AXIS); accretionDisk.setRotate(75+random.nextInt(20)); accretionDisk.setMaterial(new PhongMaterial(Color.rgb(255,180,80,0.4))); accretionDisk.setEffect(new Glow(1.8)); blackHoleLight=new PointLight(Color.rgb(120,80,200,0.6)); blackHoleLight.setMaxRange(8000); blackHoleGroup.getChildren().addAll(blackHoleSphere,blackHoleGlow,accretionDisk,blackHoleLight); blackHoleGroup.setTranslateX(blackHoleX); blackHoleGroup.setTranslateY(blackHoleY); blackHoleGroup.setTranslateZ(blackHoleZ); root3D.getChildren().add(blackHoleGroup); for(int i=0;i<80;i++) createBlackHoleParticle(); updateHUDText(); }
    private void createBlackHoleParticle() { Sphere p=new Sphere(0.8+random.nextDouble()*1.5); p.setMaterial(new PhongMaterial(Color.rgb(200+random.nextInt(55),150+random.nextInt(80),100,0.7))); p.setEffect(new Glow(1.2)); double angle=random.nextDouble()*Math.PI*2, dist=25*3+random.nextDouble()*25*4; p.setTranslateX(blackHoleX+Math.cos(angle)*dist); p.setTranslateY(blackHoleY+(random.nextDouble()-0.5)*25*2); p.setTranslateZ(blackHoleZ+Math.sin(angle)*dist); root3D.getChildren().add(p); blackHoleParticles.add(p); }

    private void launchComet(int target) {
        if(cometActive) return;
        // target: 0 = Солнце, 1-8 = планеты
        // Сохраняем как есть: 0 - Солнце, 1-8 - планеты
        targetIndex = target;

        cometGroup=new Group();
        cometNucleus=new Sphere(5);
        cometNucleus.setMaterial(new PhongMaterial(Color.rgb(180,80,40)));
        cometNucleus.setEffect(new Glow(1.2));
        cometInnerGlow=new Sphere(7.5);
        cometInnerGlow.setMaterial(new PhongMaterial(Color.rgb(255,100,30,0.6)));
        cometInnerGlow.setEffect(new Glow(1.5));
        cometOuterGlow=new Sphere(11);
        cometOuterGlow.setMaterial(new PhongMaterial(Color.rgb(255,70,20,0.3)));
        cometOuterGlow.setEffect(new Glow(2.0));
        cometGroup.getChildren().addAll(cometNucleus,cometInnerGlow,cometOuterGlow);
        cometTail=new ArrayList<>();
        tailGlowParticles=new ArrayList<>();
        for(int i=0;i<35;i++){
            Box b=new Box(2.2,2.2,4.5);
            double t=1-i/35.0;
            b.setMaterial(new PhongMaterial(Color.rgb(255,(int)(100+155*t),(int)(20+60*t),Math.max(0.12,0.8-i*0.018))));
            b.setEffect(new Glow(1.4-i*0.03));
            cometTail.add(b);
            cometGroup.getChildren().add(b);
            if(i%2==0){
                Sphere g=new Sphere(1+random.nextDouble()*1.5);
                g.setMaterial(new PhongMaterial(Color.rgb(255,120+random.nextInt(80),30,0.5)));
                g.setEffect(new Glow(1.2));
                tailGlowParticles.add(g);
                cometGroup.getChildren().add(g);
            }
        }
        if(cometLight!=null) root3D.getChildren().remove(cometLight);
        cometLight=new PointLight();
        cometLight.setMaxRange(12000);
        if(cometFireLight!=null) root3D.getChildren().remove(cometFireLight);
        cometFireLight=new PointLight();
        cometFireLight.setMaxRange(18000);
        root3D.getChildren().addAll(cometGroup,cometLight,cometFireLight);

        double sd = (target == 0) ? 8500 : 6500;
        double ang = random.nextDouble() * Math.PI * 2;
        double h = (random.nextDouble() - 0.5) * 2500;
        double sx, sy, sz;

        if(target == 0){
            sx = Math.cos(ang) * sd;
            sy = h;
            sz = Math.sin(ang) * sd;
            System.out.println("Комета запущена к СОЛНЦУ!");
        } else {
            // target от 1 до 8 - индекс планеты в списке solarSystem
            List<CelestialBody> bodies = solarSystem.getBodies();
            if (target < bodies.size()) {
                CelestialBody tg = bodies.get(target);
                sx = tg.x + Math.cos(ang) * sd;
                sy = tg.y + h;
                sz = tg.z + Math.sin(ang) * sd;
                String[] names = {"", "Меркурий", "Венера", "Земля", "Марс", "Юпитер", "Сатурн", "Уран", "Нептун"};
                System.out.println("Комета запущена к планете: " + (target <= 8 ? names[target] : "Неизвестно"));
            } else {
                return;
            }
        }
        cometGroup.setTranslateX(sx);
        cometGroup.setTranslateY(sy);
        cometGroup.setTranslateZ(sz);
        cometActive=true;
        cometTrailParticles.clear();
        cometHeat=0.5;
        followYaw=0;
        followPitch=0;
        updateHUDText();
    }

    private String getPlanetName(int n) {
        if (n == 0) return "Солнце";
        String[] names = {"", "Меркурий", "Венера", "Земля", "Марс", "Юпитер", "Сатурн", "Уран", "Нептун"};
        return n >= 1 && n <= 8 ? names[n] : "Неизвестно";
    }

    private void updateCometTail(double x,double y,double z,double vx,double vy,double vz) { if(cometTail==null) return; double sp=Math.hypot(vx,Math.hypot(vy,vz)); if(sp<0.1) return; double dx=-vx/sp,dy=-vy/sp,dz=-vz/sp,tl=5.5+sp/5+cometHeat*3; for(int i=0;i<cometTail.size();i++){ Box f=cometTail.get(i); double dist=(i+1)*tl; f.setTranslateX(x+dx*dist); f.setTranslateY(y+dy*dist+Math.sin(i*0.5)*1.2); f.setTranslateZ(z+dz*dist); f.setRotate(Math.toDegrees(Math.atan2(dz,dx))); f.setRotationAxis(Rotate.Y_AXIS); double sc=(0.6+i/(double)cometTail.size()*1.4)*(1+cometHeat*0.5); f.setWidth(2.2*sc); f.setHeight(2.2*sc); f.setDepth(4.5*(0.7+i*0.03)); } for(int i=0;i<tailGlowParticles.size();i++){ Sphere g=tailGlowParticles.get(i); double off=(i+1)*(4.2+cometHeat*2); g.setTranslateX(x+dx*off+dz*Math.sin(i*0.8)*2.5); g.setTranslateY(y+dy*off+Math.cos(i*0.6)*1.8); g.setTranslateZ(z+dz*off-dx*Math.sin(i*0.8)*2.5); g.setEffect(new Glow(0.8+Math.sin(System.currentTimeMillis()*0.008+i)*0.4+cometHeat*0.5)); } }

    private void explodePlanet(int idx) {
        if(idx < 1 || idx >= planetSpheres.size()) return;
        Sphere pl = planetSpheres.get(idx);
        CelestialBody bd = solarSystem.getBodies().get(idx);
        double px = pl.getTranslateX(), py = pl.getTranslateY(), pz = pl.getTranslateZ();

        bd.isDestroyed = true;
        activeImpulses.add(new GravitationalImpulse(px, py, pz, bd.radius * 12, bd.radius * 8));

        Sphere flash = new Sphere(bd.radius * 2);
        flash.setMaterial(new PhongMaterial(Color.rgb(255, 200, 100, 0.8)));
        flash.setEffect(new Glow(2.0));
        flash.setTranslateX(px); flash.setTranslateY(py); flash.setTranslateZ(pz);
        root3D.getChildren().add(flash);

        new Timeline(new KeyFrame(Duration.seconds(0), e->{flash.setScaleX(1);flash.setScaleY(1);flash.setScaleZ(1);}),
                new KeyFrame(Duration.seconds(0.4), e->{flash.setScaleX(2.5);flash.setScaleY(2.5);flash.setScaleZ(2.5);}),
                new KeyFrame(Duration.seconds(1.2), e->root3D.getChildren().remove(flash))).play();

        for(int i=0; i<150; i++){
            Sphere fr = new Sphere(0.3 + random.nextDouble() * bd.radius * 0.8);
            fr.setMaterial(new PhongMaterial(bd.color));
            fr.setTranslateX(px + (random.nextDouble()-0.5) * bd.radius * 2);
            fr.setTranslateY(py + (random.nextDouble()-0.5) * bd.radius * 2);
            fr.setTranslateZ(pz + (random.nextDouble()-0.5) * bd.radius * 2);
            root3D.getChildren().add(fr);
            double sp = 15 + random.nextDouble() * 40, a1 = random.nextDouble()*Math.PI*2, a2 = random.nextDouble()*Math.PI;
            activeFragments.add(new Fragment(fr, bd.vx + Math.sin(a1)*Math.cos(a2)*sp, bd.vy + Math.sin(a1)*Math.sin(a2)*sp*0.5, bd.vz + Math.cos(a1)*sp));
        }
        clearCometTrail();
        updateHUDText();
    }

    private void explodeSun() {
        Sphere flash = new Sphere(600);
        flash.setMaterial(new PhongMaterial(Color.WHITE));
        flash.setEffect(new Glow(1.2));
        root3D.getChildren().add(flash);
        CelestialBody sun = solarSystem.getBodies().get(0);
        sun.isDestroyed = true;
        for(int i=0; i<1500; i++){
            Sphere fr = new Sphere(1.5 + random.nextDouble()*15);
            double t = (double)i/1500;
            fr.setMaterial(new PhongMaterial(Color.rgb(255, Math.max(0,(int)(200*(1-t*1.2))), Math.max(0,(int)(80*(1-t*1.8))), Math.max(0,(int)(255*(1-t*0.6))))));
            double a1 = random.nextDouble()*Math.PI*2, a2 = random.nextDouble()*Math.PI, d = 200 + random.nextDouble()*800;
            fr.setTranslateX(Math.sin(a1)*Math.cos(a2)*d);
            fr.setTranslateY(Math.sin(a1)*Math.sin(a2)*d*0.7);
            fr.setTranslateZ(Math.cos(a1)*d);
            root3D.getChildren().add(fr);
            double sp = 35 + random.nextDouble()*80;
            activeFragments.add(new Fragment(fr, Math.sin(a1)*Math.cos(a2)*sp, Math.sin(a1)*Math.sin(a2)*sp*0.7, Math.cos(a1)*sp));
        }
        System.out.println("Солнце уничтожено");
        new Timeline(new KeyFrame(Duration.seconds(0.7), e->{flash.setScaleX(5);flash.setScaleY(5);flash.setScaleZ(5);}),
                new KeyFrame(Duration.seconds(4), e->root3D.getChildren().remove(flash))).play();
        clearCometTrail();
        updateHUDText();
    }

    private void clearCometTrail() { if(cometLight!=null){root3D.getChildren().remove(cometLight); cometLight=null;} if(cometFireLight!=null){root3D.getChildren().remove(cometFireLight); cometFireLight=null;} for(Sphere t:cometTrailParticles) root3D.getChildren().remove(t); cometTrailParticles.clear(); cometActive=false; if(cometGroup!=null){root3D.getChildren().remove(cometGroup); cometGroup=null;} }
    private void resetSystem() { root3D.getChildren().clear(); activeFragments.clear(); activeImpulses.clear(); planetSpheres.clear(); orbitLines.clear(); asteroids.clear(); starDust.clear(); destroyedPlanets=0; clearCometTrail(); if(blackHoleGroup!=null){root3D.getChildren().remove(blackHoleGroup); blackHoleGroup=null;} for(Sphere p:new ArrayList<>(blackHoleParticles)) root3D.getChildren().remove(p); blackHoleParticles.clear(); blackHoleActive=false; solarSystem=new SolarSystem(); setupLights(); createSun(); createPlanets(); createOrbits(); createStars(); createStarDust(); createAsteroidBelt(); updateHUDText(); }

    private void startGameLoop() {
        new AnimationTimer() {
            double lastTime = 0;
            @Override public void handle(long now) {
                if(lastTime==0) lastTime=now;
                double frameDt = Math.min((now-lastTime)/1_000_000_000.0, 0.1);
                lastTime = now;
                updateCameraFollow();
                if(isRunning) {
                    physicsTimeAcc += frameDt * timeSpeed;
                    while(physicsTimeAcc >= PHYSICS_DT) {
                        solarSystem.stepPhysics(PHYSICS_DT);
                        syncPhysicsToVisuals();
                        physicsTimeAcc -= PHYSICS_DT;
                    }
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
                if (i < planetRotations.size()) planetRotations.remove(i);
                planetSpheres.remove(i);

                int orbitIdx = i - 1;
                if(orbitIdx >= 0 && orbitIdx < orbitLines.size() && orbitLines.get(orbitIdx) != null){
                    for(Sphere d : orbitLines.get(orbitIdx).dots) root3D.getChildren().remove(d);
                    orbitLines.remove(orbitIdx);
                }

                if (i < bodies.size()) bodies.remove(i);
                destroyedPlanets++;
                updateHUDText();
            } else {
                CelestialBody b = bodies.get(i);
                planetSpheres.get(i).setTranslateX(b.x);
                planetSpheres.get(i).setTranslateY(b.y);
                planetSpheres.get(i).setTranslateZ(b.z);
                if(i < planetRotations.size()) planetRotations.get(i).setAngle(planetRotations.get(i).getAngle() + 3 * timeSpeed);
            }
        }

        if(saturnRings != null && bodies.size() > 5 && !bodies.get(5).isDestroyed) {
            saturnRings.setTranslateX(bodies.get(5).x);
            saturnRings.setTranslateY(bodies.get(5).y);
            saturnRings.setTranslateZ(bodies.get(5).z);
        }
        if(earthMoon != null && bodies.size() > 2 && !bodies.get(2).isDestroyed) {
            CelestialBody earth = bodies.get(2);
            double ma = System.currentTimeMillis() * 0.0008, md = 28;
            earthMoon.setTranslateX(earth.x + md * Math.cos(ma));
            earthMoon.setTranslateY(earth.y + 2 * Math.sin(ma * 1.3));
            earthMoon.setTranslateZ(earth.z + md * Math.sin(ma));
        }

        if(blackHoleGroup != null) {
            CelestialBody bh = null;
            for(CelestialBody b : bodies) { if(b.isBlackHole && !b.isDestroyed) { bh = b; break; } }
            if(bh != null) {
                blackHoleGroup.setTranslateX(bh.x);
                blackHoleGroup.setTranslateY(bh.y);
                blackHoleGroup.setTranslateZ(bh.z);
                accretionDisk.setRotate(accretionDisk.getRotate() + 2.5 * timeSpeed);

                List<Sphere> toRemove = new ArrayList<>();
                int toCreate = 0;

                for (Sphere p : blackHoleParticles) {
                    double dx = bh.x - p.getTranslateX(), dy = bh.y - p.getTranslateY(), dz = bh.z - p.getTranslateZ();
                    double dist = Math.hypot(dx, Math.hypot(dy, dz));
                    if (dist < bh.radius * 1.5) {
                        toRemove.add(p);
                        toCreate++;
                    } else {
                        double f = bh.mass * 0.005 / (dist * dist + 50);
                        p.setTranslateX(p.getTranslateX() + dx / dist * f * timeSpeed);
                        p.setTranslateY(p.getTranslateY() + dy / dist * f * 0.3 * timeSpeed);
                        p.setTranslateZ(p.getTranslateZ() + dz / dist * f * timeSpeed);
                        p.setRotate(p.getRotate() + 4);
                    }
                }

                for (Sphere p : toRemove) {
                    root3D.getChildren().remove(p);
                    blackHoleParticles.remove(p);
                }
                for (int k = 0; k < toCreate; k++) {
                    createBlackHoleParticle();
                }
            } else {
                blackHoleActive = false;
                root3D.getChildren().remove(blackHoleGroup);
                blackHoleGroup = null;
                for(Sphere p : new ArrayList<>(blackHoleParticles)) root3D.getChildren().remove(p);
                blackHoleParticles.clear();
                updateHUDText();
            }
        }
    }

    private void updateComet() {
        if(!cometActive || cometGroup==null) return;
        cometNucleus.setRotate(cometNucleus.getRotate()+3);
        cometInnerGlow.setRotate(cometInnerGlow.getRotate()+1.5);

        double tx=0,ty=0,tz=0;
        double cr = (targetIndex == 0) ? 100 : 60;

        if(targetIndex == 0){
            tx=0;ty=0;tz=0;
        } else if(targetIndex >= 1 && targetIndex < planetSpheres.size()){
            Sphere tg = planetSpheres.get(targetIndex);
            tx = tg.getTranslateX();
            ty = tg.getTranslateY();
            tz = tg.getTranslateZ();
            cr = solarSystem.getBodies().get(targetIndex).radius + 25;
        } else {
            cometActive=false;
            return;
        }

        double cx = cometGroup.getTranslateX();
        double cy = cometGroup.getTranslateY();
        double cz = cometGroup.getTranslateZ();
        double dx = tx-cx, dy = ty-cy, dz = tz-cz, dist = Math.hypot(dx, Math.hypot(dy,dz));

        if(dist < cr){
            root3D.getChildren().remove(cometGroup);
            clearCometTrail();
            if(targetIndex == 0) explodeSun();
            else explodePlanet(targetIndex);
            return;
        }

        double bs = (targetIndex == 0) ? 9 : 11.5;
        double sf = Math.min(1, dist/2500);
        double sp = bs * (0.5 + sf * 0.5) + cometHeat * 1.5;
        double vx = dx/dist * sp, vy = dy/dist * sp, vz = dz/dist * sp;
        cometGroup.setTranslateX(cx + vx);
        cometGroup.setTranslateY(cy + vy);
        cometGroup.setTranslateZ(cz + vz);

        if(cometLight != null){
            cometLight.setTranslateX(cx + vx);
            cometLight.setTranslateY(cy + vy);
            cometLight.setTranslateZ(cz + vz);
        }
        if(cometFireLight != null){
            cometFireLight.setTranslateX(cx + vx);
            cometFireLight.setTranslateY(cy + vy);
            cometFireLight.setTranslateZ(cz + vz);
        }
        updateCometTail(cx+vx, cy+vy, cz+vz, vx, vy, vz);
        double temp = Math.min(1, 1 - dist/2000);
        cometHeat = temp;
    }

    private void updateImpulses() { Iterator<GravitationalImpulse> impIt=activeImpulses.iterator(); while(impIt.hasNext()){ GravitationalImpulse imp=impIt.next(); imp.life-=0.02; if(imp.life<=0){ impIt.remove(); continue; } for(int i=0;i<planetSpheres.size();i++){ Sphere pl=planetSpheres.get(i); double dx=imp.x-pl.getTranslateX(), dy=imp.y-pl.getTranslateY(), dz=imp.z-pl.getTranslateZ(), dist=Math.hypot(dx,Math.hypot(dy,dz)); if(dist<imp.radius){ double force=imp.strength*(1-dist/imp.radius)*imp.life/(dist+5)*timeSpeed*1.5; pl.setTranslateX(pl.getTranslateX()+dx/dist*force*0.5); pl.setTranslateY(pl.getTranslateY()+dy/dist*force*0.2); pl.setTranslateZ(pl.getTranslateZ()+dz/dist*force*0.5); } } } }
    private void updateFragments(){ Iterator<Fragment> it=activeFragments.iterator(); while(it.hasNext()){ Fragment f=it.next(); f.sphere.setTranslateX(f.sphere.getTranslateX()+f.vx*timeSpeed); f.sphere.setTranslateY(f.sphere.getTranslateY()+f.vy*timeSpeed); f.sphere.setTranslateZ(f.sphere.getTranslateZ()+f.vz*timeSpeed); f.sphere.setRotate(f.sphere.getRotate()+5); f.vx*=0.995; f.vy*=0.995; f.vz*=0.995; f.life-=0.001*timeSpeed; if(f.life<=0){ root3D.getChildren().remove(f.sphere); it.remove(); } } }

    private void setupControls() {
        scene.setOnMousePressed(e->{ if(e.isSecondaryButtonDown()){ rightMousePressed=true; mouseOldX=e.getSceneX(); mouseOldY=e.getSceneY(); } });
        scene.setOnMouseReleased(e->rightMousePressed=false);
        scene.setOnMouseDragged(e->{ if(rightMousePressed){ double dx=e.getSceneX()-mouseOldX,dy=e.getSceneY()-mouseOldY; if(cameraMode==CameraMode.FOLLOW_COMET && cometActive){ followYaw+=dx*0.5; followPitch+=dy*0.3; followPitch=Math.max(-30,Math.min(30,followPitch)); } else if(cameraMode==CameraMode.FREE){ cameraRotateY.setAngle(cameraRotateY.getAngle()-dx*0.4); cameraRotateX.setAngle(cameraRotateX.getAngle()-dy*0.4); cameraRotateX.setAngle(Math.max(-89,Math.min(89,cameraRotateX.getAngle()))); } mouseOldX=e.getSceneX(); mouseOldY=e.getSceneY(); } });
        scene.setOnKeyPressed(e->{
            switch(e.getCode()){
                case SPACE: isRunning=!isRunning; System.out.println(isRunning?"Система запущена":"Система на паузе"); break;
                case ADD: case EQUALS: timeSpeed*=1.5; timeSpeed=Math.min(timeSpeed,8.0); break;
                case SUBTRACT: case MINUS: timeSpeed/=1.5; timeSpeed=Math.max(timeSpeed,0.1); break;
                case R: camera.setTranslateX(0);camera.setTranslateY(0);camera.setTranslateZ(2800);cameraRotateY.setAngle(180);cameraRotateX.setAngle(0);followYaw=0;followPitch=0;cameraMode=CameraMode.FREE; break;
                case C: if(cometActive) cameraMode=CameraMode.FOLLOW_COMET; break;
                case F: cameraMode=CameraMode.FREE; break;
                case B: spawnBlackHole(); break;
                case X: resetSystem(); break;
                case DIGIT0: launchComet(0); break;
                case DIGIT1: launchComet(1); break;
                case DIGIT2: launchComet(2); break;
                case DIGIT3: launchComet(3); break;
                case DIGIT4: launchComet(4); break;
                case DIGIT5: launchComet(5); break;
                case DIGIT6: launchComet(6); break;
                case DIGIT7: launchComet(7); break;
                case DIGIT8: launchComet(8); break;
                case W: wPressed=true; break;
                case A: aPressed=true; break;
                case S: sPressed=true; break;
                case D: dPressed=true; break;
                case Q: qPressed=true; break;
                case E: ePressed=true; break;
                default: break;
            }
        });
        scene.setOnKeyReleased(e->{ switch(e.getCode()){ case W: wPressed=false; break; case A: aPressed=false; break; case S: sPressed=false; break; case D: dPressed=false; break; case Q: qPressed=false; break; case E: ePressed=false; break; default: break; } });
    }

    private void startMovementTimer() { new AnimationTimer() { @Override public void handle(long now) { if(cameraMode==CameraMode.FREE){ double sp=moveSpeed,ay=Math.toRadians(cameraRotateY.getAngle()),ax=Math.toRadians(cameraRotateX.getAngle()),mx=0,my=0,mz=0; if(wPressed){ mx+=Math.sin(ay)*Math.cos(ax)*sp; my+=Math.sin(ax)*sp; mz+=Math.cos(ay)*Math.cos(ax)*sp; } if(sPressed){ mx-=Math.sin(ay)*Math.cos(ax)*sp; my-=Math.sin(ax)*sp; mz-=Math.cos(ay)*Math.cos(ax)*sp; } if(aPressed){ mx-=Math.cos(ay)*sp; mz+=Math.sin(ay)*sp; } if(dPressed){ mx+=Math.cos(ay)*sp; mz-=Math.sin(ay)*sp; } if(qPressed) my-=sp; if(ePressed) my+=sp; camera.setTranslateX(camera.getTranslateX()+mx); camera.setTranslateY(camera.getTranslateY()+my); camera.setTranslateZ(camera.getTranslateZ()+mz); } } }.start(); }
    private void updateCameraFollow() { if(cameraMode==CameraMode.FOLLOW_COMET && cometActive && cometGroup!=null){ double cx=cometGroup.getTranslateX(),cy=cometGroup.getTranslateY(),cz=cometGroup.getTranslateZ(); double dirX=0,dirZ=-1; if(targetIndex>=1 && targetIndex<planetSpheres.size()){ Sphere tg=planetSpheres.get(targetIndex); double dx=tg.getTranslateX()-cx,dz=tg.getTranslateZ()-cz,len=Math.hypot(dx,dz); if(len>0.01){ dirX=dx/len; dirZ=dz/len; } } else if(targetIndex==0){ double dx=-cx,dz=-cz,len=Math.hypot(dx,dz); if(len>0.01){ dirX=dx/len; dirZ=dz/len; } } camera.setTranslateX(cx-dirX*followDistance); camera.setTranslateY(cy+followHeight); camera.setTranslateZ(cz-dirZ*followDistance); double ang=Math.toDegrees(Math.atan2(cx-camera.getTranslateX(),cz-camera.getTranslateZ())); cameraRotateY.setAngle(ang+followYaw); cameraRotateX.setAngle(followPitch); } }
    private void createSaturnRings() { if(planetSpheres.size()<=5) return; saturnRings=new Group(); double[] rr={24,29,35},rw={4.5,3.2,2.8}; for(int i=0;i<rr.length;i++){ Cylinder c=new Cylinder(rr[i],rw[i]); c.setRotationAxis(Rotate.X_AXIS); c.setRotate(82); c.setMaterial(new PhongMaterial(Color.rgb(230,210,170,0.65+i*0.1))); saturnRings.getChildren().add(c); } root3D.getChildren().add(saturnRings); }
    private void createEarthMoon() { earthMoon=new Sphere(3.8); earthMoon.setMaterial(new PhongMaterial(Color.LIGHTGRAY)); root3D.getChildren().add(earthMoon); }
    public static void main(String[] args) { launch(args); }
}
//Проверка Comita