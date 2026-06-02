package solarsystem.patterns.factory;

import solarsystem.CelestialBody;
import solarsystem.patterns.strategy.OrbitalStrategy;
import javafx.scene.paint.Color;

public class PlanetFactory implements CelestialFactory {
    @Override
    public CelestialBody create(String name, double radius, double mass, double orbitRadius, Color color) {

        CelestialBody planet = new CelestialBody(name, radius, mass, color, orbitRadius, 0);


        new OrbitalStrategy().applyBehavior(planet);

        return planet;
    }

    @Override
    public String getFactoryName() {
        return "PlanetFactory";
    }
}