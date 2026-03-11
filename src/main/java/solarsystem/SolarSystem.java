package solarsystem;

import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;

public class SolarSystem {
    private final List<CelestialBody> planets = new ArrayList<>();
    private final CelestialBody sun;

    public SolarSystem() {
        sun = new CelestialBody("Солнце", 30, Color.YELLOW, 0, 0, 0);
        createPlanets();
    }

    private void createPlanets() {
        planets.add(new CelestialBody("Меркурий",  4, Color.GRAY,                80, 0, 0.040));
        planets.add(new CelestialBody("Венера",    6, Color.rgb(255, 200, 100),  120, 1, 0.025));
        planets.add(new CelestialBody("Земля",     7, Color.BLUE,                160, 2, 0.015));
        planets.add(new CelestialBody("Марс",      5, Color.RED,                 200, 3, 0.012));
        planets.add(new CelestialBody("Юпитер",   15, Color.rgb(200, 150, 100),  280, 4, 0.005));
        planets.add(new CelestialBody("Сатурн",   12, Color.rgb(220, 200, 150),  360, 5, 0.003));
        planets.add(new CelestialBody("Уран",     10, Color.CYAN,                440, 6, 0.002));
        planets.add(new CelestialBody("Нептун",   10, Color.BLUE.darker(),       520, 7, 0.0015));
    }

    public void updatePositions(double timeSpeed) {
        for (CelestialBody planet : planets) {
            planet.move(timeSpeed);
        }
    }

    public CelestialBody getSun() {
        return sun;
    }

    public List<CelestialBody> getPlanets() {
        return planets;
    }
}