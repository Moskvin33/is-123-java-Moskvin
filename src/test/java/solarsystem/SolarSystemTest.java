package solarsystem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SolarSystemTest {

    @Test
    void testInitialization() {
        // Проверяем, что система создается и в ней есть тела
        SolarSystem system = new SolarSystem();
        assertTrue(system.getBodies().size() > 0, "Solar system should have bodies");

        // Проверяем, что первое тело - Солнце
        CelestialBody sun = system.getBodies().get(0);
        assertEquals("Солнце", sun.name, "First body should be Sun");
    }

    @Test
    void testAddBody() {
        SolarSystem system = new SolarSystem();
        int initialSize = system.getBodies().size();

        // Добавляем новое тело
        CelestialBody newPlanet = new CelestialBody("TestPlanet", 10, 50, javafx.scene.paint.Color.RED, 100, 0);
        system.addBody(newPlanet);

        assertEquals(initialSize + 1, system.getBodies().size(), "Body count should increase");
    }

    @Test
    void testStepPhysicsDoesNotCrash() {
        SolarSystem system = new SolarSystem();
        // Проверяем, что шаг физики выполняется без ошибок (не падает)
        assertDoesNotThrow(() -> {
            system.stepPhysics(0.016);
        }, "stepPhysics should not throw exceptions");
    }
}