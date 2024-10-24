package ru.pobopo.smartthing.gateway.service.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import ru.pobopo.smartthing.gateway.service.device.api.RestDeviceApi;
import ru.pobopo.smartthing.model.gateway.ObservableType;
import ru.pobopo.smartthing.model.gateway.dashboard.DashboardGroup;
import ru.pobopo.smartthing.model.gateway.dashboard.DashboardObservable;
import ru.pobopo.smartthing.model.gateway.dashboard.DashboardObservableValueUpdate;
import ru.pobopo.smartthing.model.gateway.dashboard.DashboardObservableValue;
import ru.pobopo.smartthing.model.InternalHttpResponse;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class DashboardGroupWorker extends Thread {
    @Getter
    private volatile DashboardGroup group;
    private final ObjectMapper objectMapper;
    private final RestDeviceApi deviceApi;
    private final Consumer<List<DashboardObservableValueUpdate>> updatesConsumer;

    @Setter
    private boolean running = true;

    @Getter
    private final Map<DashboardObservable, Deque<DashboardObservableValue>> values = new ConcurrentHashMap<>();

    public DashboardGroupWorker(
            DashboardGroup group,
            RestDeviceApi deviceApi,
            ObjectMapper objectMapper,
            Consumer<List<DashboardObservableValueUpdate>> updatesConsumer
    ) {
        super("Dashboard-Group-Worker-" + group.getDevice().getName());
        this.group = group;
        this.objectMapper = objectMapper;
        this.deviceApi = deviceApi;
        this.updatesConsumer = updatesConsumer;
        // todo load old values
    }

    public void updateGroup(DashboardGroup group) {
        Objects.requireNonNull(group);
        this.group = group;
        this.values.keySet().forEach((obs) -> {
            if (!this.group.getObservables().contains(obs)) {
                values.remove(obs);
            }
        });
    }

    @Override
    public void run() {
        Objects.requireNonNull(group);

        while (running) {
            try {
                update();
                Thread.sleep(group.getConfig().getUpdateDelay());
            } catch (InterruptedException exception) {
                log.warn("Worker {} interrupted", group.getId());
            }
        }
        log.info("Worker {} stopped", group.getId());
    }

    public synchronized void update() {
        if (group.getObservables().isEmpty()) {
            return;
        }

        try {
            Map<String, Object> sensors = fetchValues(ObservableType.sensor);
            Map<String, Object> states = fetchValues(ObservableType.state);

            if (sensors.isEmpty() && states.isEmpty()) {
                return;
            }

            List<DashboardObservableValueUpdate> updates = new ArrayList<>();
            for (DashboardObservable observable: group.getObservables()) {
                Object value;
                switch (observable.getType()) {
                    case state -> value = states.get(observable.getName());
                    case sensor -> value = sensors.get(observable.getName());
                    default -> throw new IllegalArgumentException("Type " + observable.getType() + " not supported!");
                }

                if (value == null) {
                    continue;
                }

                values.putIfAbsent(observable, new ArrayDeque<>(50));
                Deque<DashboardObservableValue> observableValues = values.get(observable);

                DashboardObservableValue observableValue = new DashboardObservableValue(value, LocalDateTime.now());
                observableValues.addFirst(observableValue);
                if (observableValues.size() >= 50) {
                    observableValues.removeLast();
                }
                updates.add(new DashboardObservableValueUpdate(observable, observableValue));
            }
            
            updatesConsumer.accept(updates);
        } catch (JsonProcessingException exception) {
            log.error("Failed to process values", exception);
        }
    }

    @NonNull
    private Map<String, Object> fetchValues(ObservableType type) throws JsonProcessingException {
        log.debug("Trying to update {} values", type);
        List<DashboardObservable> observables = group.getObservables()
                .stream()
                .filter((obs) -> obs.getType().equals(type))
                .toList();

        if (observables.isEmpty()) {
            log.debug("No {}S, skipping update", type);
            return Map.of();
        }
        InternalHttpResponse response;
        switch (type) {
            case sensor -> response = deviceApi.getSensors(group.getDevice());
            case state -> response = deviceApi.getStates(group.getDevice());
            default -> {
                throw new IllegalArgumentException("Type " + type + " not supported!");
            }
        }
        if (response.getStatus() != HttpStatus.OK) {
            log.error("Failed to fetch sensors values");
            return Map.of();
        }
        return objectMapper.readValue(response.getData(), new TypeReference<>() {});
    }
}
