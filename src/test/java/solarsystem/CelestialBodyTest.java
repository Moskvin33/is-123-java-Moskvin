package solarsystem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.paint.Color;

class CelestialBodyTest {

    @Test
    void testConstructor() {
        CelestialBody earth = new CelestialBody("Earth", 10, 500, Color.BLUE, 200, 100);

        assertEquals("Earth", earth.name);
        assertEquals(10, earth.radius);
        assertEquals(500, earth.mass);
        assertEquals(200, earth.x);
        assertEquals(100, earth.z);
        assertEquals(0, earth.y); // В вашем конструкторе Y инициализируется 0
    }

    @Test
    void testIntegrationPosition() {
        CelestialBody body = new CelestialBody("Test", 1, 1, Color.WHITE, 0, 0);
        body.vx = 10; // Задаем скорость
        body.vz = 0;

        body.integratePosition(1.0); // Двигаем на 1 секунду

        // Позиция должна измениться
        assertTrue(body.x > 0, "Body should move along X axis");
    }

    @Test
    void testBlackHoleFlag() {
        CelestialBody bh = new CelestialBody("BH", 50, 10000, Color.BLACK, 0, 0);
        assertFalse(bh.isBlackHole, "Black hole flag should be false by default");

        bh.isBlackHole = true;
        assertTrue(bh.isBlackHole, "Black hole flag should be true after setting");
    }
}