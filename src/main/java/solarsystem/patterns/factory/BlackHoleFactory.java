package solarsystem.patterns.factory;

import solarsystem.CelestialBody;
import solarsystem.patterns.strategy.BlackHoleStrategy;
import javafx.scene.paint.Color;

public class BlackHoleFactory implements CelestialFactory {
    @Override
    public CelestialBody create(String name, double radius, double mass, double orbitRadius, Color color) {
        CelestialBody blackHole = new CelestialBody(name, radius, mass, color, orbitRadius, 0);

        // Стратегия сама выставит массу 25000, радиус 25 и обнулит скорость
        new BlackHoleStrategy().applyBehavior(blackHole);

        return blackHole;
    }

    @Override
    public String getFactoryName() {
        return "BlackHoleFactory";
    }
}