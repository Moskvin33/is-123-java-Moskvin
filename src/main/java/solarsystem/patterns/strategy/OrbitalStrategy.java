package solarsystem.patterns.strategy;

import solarsystem.CelestialBody;

public class OrbitalStrategy implements MovementStrategy {
    private static final double SUN_MASS = 20000.0;
    private static final double G = 40.0;

    @Override
    public void applyBehavior(CelestialBody body) {
        double r = Math.hypot(body.getX(), body.getZ());
        if (r > 1.0) {
            // v = √(GM/r)
            double v = Math.sqrt(G * SUN_MASS / r);
            body.vx = -v * (body.getZ() / r);
            body.vz = v * (body.getX() / r);
        }
    }

    @Override
    public String getName() {
        return "OrbitalStrategy";
    }
}