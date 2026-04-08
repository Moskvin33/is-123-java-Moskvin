package solarsystem;
import javafx.scene.paint.Color;

public class CelestialBody {
    public final String name;
    public double radius, mass;
    public final Color color;
    public double x, y, z;
    public double vx, vy, vz;
    public double ax, ay, az;
    public boolean isDestroyed = false;
    public boolean isBlackHole = false;

    public CelestialBody(String name, double radius, double mass, Color color, double x, double z) {
        this.name = name;
        this.radius = radius;
        this.mass = mass;
        this.color = color;
        this.x = x;
        this.z = z;
        this.y = 0;
        this.vx = this.vy = this.vz = 0;
        this.ax = this.ay = this.az = 0;
    }

    public void integratePosition(double dt) {
        // Velocity Verlet integration
        x += vx * dt + 0.5 * ax * dt * dt;
        y += vy * dt + 0.5 * ay * dt * dt;
        z += vz * dt + 0.5 * az * dt * dt;

        vx += ax * dt;
        vy += ay * dt;
        vz += az * dt;
    }

    public double getRadius() { return radius; }
    public double getMass() { return mass; }
    public Color getColor() { return color; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
//Проверка Comita