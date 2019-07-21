package com.boclips.eventbus.infrastructure;

import com.boclips.eventbus.ConflictingSubscriberException;
import com.boclips.eventbus.EventBus;
import com.boclips.eventbus.EventHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SynchronousFakeEventBus implements EventBus {
    private Map<Class<?>, EventHandler<?>> handlerByEvent = new HashMap<>();
    private List<Object> allEvents = new ArrayList<>();

    @Override
    public <T> void subscribe(Class<T> eventType, EventHandler<T> eventHandler) {
        handlerByEvent.computeIfPresent(eventType, (cls, handler) -> {
            throw new ConflictingSubscriberException("There already is a subscription for " + eventType.getSimpleName() + ": " + handler.getClass().getSimpleName());
        });
        handlerByEvent.put(eventType, eventHandler);
        Logger.getLogger(SynchronousFakeEventBus.class.getSimpleName()).info("Subscribed handler for " + eventType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void publish(Object event) {
        allEvents.add(event);

        EventHandler<Object> eventHandler = (EventHandler<Object>) handlerByEvent.get(event.getClass());
        if (eventHandler != null) {
            eventHandler.handle(event);
        }

        Logger.getLogger(SynchronousFakeEventBus.class.getSimpleName()).info("Published event " + event.getClass());
    }

    @Override
    public void unsubscribe(Class<?> eventType) {
        handlerByEvent.remove(eventType);
    }

    public Boolean hasReceivedEventOfType(Class<?> eventType) {
        List<Object> eventsOfType = findEvent(eventType);
        return eventsOfType.size() != 0;
    }

    public <T> T getEventOfType(Class<T> eventType) {
        List<Object> eventsOfType = findEvent(eventType);

        if (eventsOfType.size() == 0) {
            throw new IllegalStateException("Found 0 events matching " + eventType);
        }

        if (eventsOfType.size() > 1) {
            throw new IllegalStateException(String.format("Found more than one (%d) events matching {%s}", eventsOfType.size(), eventType));
        }

        return (T) eventsOfType.stream().findFirst().get();
    }

    public void clearState() {
        allEvents.clear();
    }

    public List<Object> getReceivedEvents() {
        return allEvents;
    }

    public int countEventsOfType(Class<?> eventType) {
        return findEvent(eventType).size();
    }

    private List<Object> findEvent(Class<?> eventType) {
        return allEvents.stream().filter(object -> object.getClass() == eventType).collect(Collectors.toList());
    }
}