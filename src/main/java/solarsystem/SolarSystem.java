package solarsystem;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;

public class SolarSystem {
    private final List<CelestialBody> bodies = new ArrayList<>();
    private static final double G = 40.0;
    private static final double SOFTENING = 80.0;

    public SolarSystem() {
        bodies.add(new CelestialBody("Солнце", 30, 20000.0, Color.YELLOW, 0, 0));
        addPlanet("Меркурий", 4, 8.0, Color.GRAY, 95);
        addPlanet("Венера", 6, 15.0, Color.rgb(255, 200, 100), 135);
        addPlanet("Земля", 7, 20.0, Color.BLUE, 175);
        addPlanet("Марс", 5, 12.0, Color.RED, 215);
        addPlanet("Юпитер", 15, 80.0, Color.rgb(200, 150, 100), 290);
        addPlanet("Сатурн", 12, 60.0, Color.rgb(220, 200, 150), 370);
        addPlanet("Уран", 10, 40.0, Color.CYAN, 450);
        addPlanet("Нептун", 10, 35.0, Color.BLUE.darker(), 530);
        initCircularVelocities();
    }

    private void addPlanet(String name, double radius, double mass, Color color, double orbitR) {
        bodies.add(new CelestialBody(name, radius, mass, color, orbitR, 0));
    }

    private void initCircularVelocities() {
        CelestialBody sun = bodies.get(0);
        for (int i = 1; i < bodies.size(); i++) {
            CelestialBody p = bodies.get(i);
            double r = Math.hypot(p.x, p.z);
            if (r < 1.0) continue;
            double v = Math.sqrt(G * sun.mass / r);
            p.vx = -v * (p.z / r);
            p.vz = v * (p.x / r);
            p.vy = 0;
        }
    }

    public void stepPhysics(double dt) {
        dt = Math.min(dt, 0.016);
        for (CelestialBody b : bodies) {
            if (!b.isDestroyed) { b.ax = b.ay = b.az = 0; }
        }
        computeGravitationalForces();
        for (CelestialBody b : bodies) {
            if (!b.isDestroyed) { b.integratePosition(dt); }
        }
        checkCollisions();
    }

    private void computeGravitationalForces() {
        for (int i = 0; i < bodies.size(); i++) {
            CelestialBody a = bodies.get(i);
            if (a.isDestroyed) continue;
            for (int j = i + 1; j < bodies.size(); j++) {
                CelestialBody b = bodies.get(j);
                if (b.isDestroyed) continue;
                double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
                double distSq = dx*dx + dy*dy + dz*dz + SOFTENING;
                double dist = Math.sqrt(distSq);
                double forceMag = (G * a.mass * b.mass) / distSq;
                double fx = forceMag * dx / dist, fy = forceMag * dy / dist, fz = forceMag * dz / dist;
                a.ax += fx / a.mass; a.ay += fy / a.mass; a.az += fz / a.mass;
                b.ax -= fx / b.mass; b.ay -= fy / b.mass; b.az -= fz / b.mass;
            }
        }
    }

    private void checkCollisions() {
        for (int i = 0; i < bodies.size(); i++) {
            CelestialBody a = bodies.get(i);
            if (a.isDestroyed) continue;
            for (int j = i + 1; j < bodies.size(); j++) {
                CelestialBody b = bodies.get(j);
                if (b.isDestroyed) continue;
                double dist = Math.hypot(a.x - b.x, Math.hypot(a.y - b.y, a.z - b.z));
                if (a.isBlackHole && dist < a.radius * 3) {
                    b.isDestroyed = true;
                } else if (b.isBlackHole && dist < b.radius * 3) {
                    a.isDestroyed = true;
                } else if (dist < a.radius + b.radius) {
                    if (a.mass >= b.mass) {
                        a.mass += b.mass * 0.3;
                        a.radius = Math.pow(a.mass / 100, 0.33);
                        b.isDestroyed = true;
                    } else {
                        b.mass += a.mass * 0.3;
                        b.radius = Math.pow(b.mass / 100, 0.33);
                        a.isDestroyed = true;
                    }
                }
            }
        }
    }

    public List<CelestialBody> getBodies() { return bodies; }
    public void addBody(CelestialBody b) { bodies.add(b); }
}
//Проверка Comita