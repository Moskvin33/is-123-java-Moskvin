package solarsystem.patterns.strategy;

import solarsystem.CelestialBody;

public interface MovementStrategy {
    void applyBehavior(CelestialBody body);
    String getName();
}