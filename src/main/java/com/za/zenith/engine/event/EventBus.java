package com.za.zenith.engine.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe lightweight Event Bus for system decoupling in Zenith.
 * Allows high-performance publish-subscribe communication without direct dependencies.
 */
public class EventBus {
    private static final EventBus INSTANCE = new EventBus();
    
    public static EventBus getInstance() {
        return INSTANCE;
    }

    private final Map<Class<? extends Event>, List<Consumer<? extends Event>>> listeners = new ConcurrentHashMap<>();

    private EventBus() {}

    /**
     * Subscribes a listener to a specific event type.
     * Thread-safe registration using CopyOnWriteArrayList.
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(obj -> listener.accept((T) obj));
    }

    /**
     * Publishes an event to all registered listeners.
     */
    @SuppressWarnings("unchecked")
    public void publish(Event event) {
        List<Consumer<? extends Event>> list = listeners.get(event.getClass());
        if (list != null) {
            for (Consumer<? extends Event> listener : list) {
                ((Consumer<Event>) listener).accept(event);
            }
        }
    }
}
