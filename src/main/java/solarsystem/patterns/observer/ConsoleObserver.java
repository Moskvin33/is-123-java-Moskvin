package solarsystem.patterns.observer;

import java.util.Map;

public class ConsoleObserver implements SimulationObserver {
    @Override
    public void onEvent(String eventType, Map<String, Object> data) {
        String msg = "📢 [" + eventType + "] " + data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + " | " + b).orElse("");
        System.out.println(msg);
    }

    @Override
    public String getObserverName() {
        return "ConsoleObserver";
    }
}