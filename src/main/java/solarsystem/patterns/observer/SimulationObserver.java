package solarsystem.patterns.observer;

import java.util.Map;

public interface SimulationObserver {
    void onEvent(String eventType, Map<String, Object> data);
    String getObserverName();
}