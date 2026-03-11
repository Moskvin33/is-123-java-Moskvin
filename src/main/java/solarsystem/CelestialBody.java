package solarsystem;

import javafx.scene.paint.Color;

public class CelestialBody {
    private final String name;
    private final double radius;
    private final Color color;
    private final double orbitRadius;
    private double angle;
    private final double angularSpeed;
    private double x, y, z;

    public CelestialBody(String name, double radius, Color color,
                         double orbitRadius, double startAngle, double angularSpeed) {
        this.name = name;
        this.radius = radius;
        this.color = color;
        this.orbitRadius = orbitRadius;
        this.angle = startAngle;
        this.angularSpeed = angularSpeed;
        updateCoordinates();
    }

    public void move(double timeSpeed) {
        angle += angularSpeed * timeSpeed;
        updateCoordinates();
    }

    private void updateCoordinates() {
        x = orbitRadius * Math.cos(angle);
        z = orbitRadius * Math.sin(angle);
        y = 0;
    }

    public String getName()          { return name; }
    public double getX()             { return x; }
    public double getY()             { return y; }
    public double getZ()             { return z; }
    public double getRadius()        { return radius; }
    public Color  getColor()         { return color; }
    public double getOrbitRadius()   { return orbitRadius; }
}