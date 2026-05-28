package solarsystem.patterns.factory;

import solarsystem.CelestialBody;
import solarsystem.patterns.strategy.FreeFlightStrategy;
import javafx.scene.paint.Color;

public class CometFactory implements CelestialFactory {
    @Override
    public CelestialBody create(String name, double radius, double mass, double orbitRadius, Color color) {
        CelestialBody comet = new CelestialBody(name, radius, mass, color, orbitRadius, 0);

        // Кометы часто выходят за плоскость эклиптики
        comet.y = (Math.random() - 0.5) * 60;

        // Применяем стратегию хаотичного движения
        new FreeFlightStrategy().applyBehavior(comet);

        // Ускоряем и добавляем наклон
        comet.vx *= 1.8;
        comet.vz *= 1.8;
        comet.vy = (Math.random() - 0.5) * 3.0;

        return comet;
    }

    @Override
    public String getFactoryName() {
        return "CometFactory";
    }
}