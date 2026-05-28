package solarsystem.patterns.strategy;

import solarsystem.CelestialBody;
import java.util.Random;

public class FreeFlightStrategy implements MovementStrategy {
    @Override
    public void applyBehavior(CelestialBody body) {
        // Немного хаотичного движения
        Random rand = new Random();
        body.vx += (rand.nextDouble() - 0.5) * 0.5;
        body.vz += (rand.nextDouble() - 0.5) * 0.5;
    }

    @Override
    public String getName() {
        return "FreeFlightStrategy";
    }
}