package solarsystem.patterns.strategy;

import solarsystem.CelestialBody;

public class BlackHoleStrategy implements MovementStrategy {
    @Override
    public void applyBehavior(CelestialBody body) {
        body.mass = 25000.0;
        body.radius = 25.0;
        body.vx = 0;
        body.vy = 0;
        body.vz = 0;
    }

    @Override
    public String getName() {
        return "BlackHoleStrategy";
    }
}