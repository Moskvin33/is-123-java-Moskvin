package solarsystem.patterns.factory;

import solarsystem.CelestialBody;
import javafx.scene.paint.Color;

public interface CelestialFactory {
    CelestialBody create(String name, double radius, double mass, double orbitRadius, Color color);
    String getFactoryName();
}