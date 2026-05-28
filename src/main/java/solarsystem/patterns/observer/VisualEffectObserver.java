package solarsystem.patterns.observer;

import java.util.Map;

public class VisualEffectObserver implements SimulationObserver {
    @Override
    public void onEvent(String eventType, Map<String, Object> data) {
        switch (eventType) {
            case "PLANET_CREATED" ->
                    System.out.println("✨ [VFX] Glow & orbit line for: " + data.get("name"));
            case "EXPLOSION" ->
                    System.out.println("💥 [VFX] Flash, shockwave & debris particles");
            case "BLACK_HOLE_SPAWNED" ->
                    System.out.println("🕳 [VFX] Accretion disk & gravitational lensing");
            case "COMET_LAUNCHED" ->
                    System.out.println("🔥 [VFX] Fire trail & heat distortion");
            default ->
                    System.out.println("🎨 [VFX] Generic effect triggered for: " + eventType);
        }
    }

    @Override
    public String getObserverName() {
        return "VisualEffectObserver";
    }
}