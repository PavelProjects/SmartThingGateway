package ru.pobopo.smartthing.gateway.service.device;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import ru.pobopo.smartthing.gateway.service.dashboard.DashboardGroupWorkerFactory;
import ru.pobopo.smartthing.gateway.repository.FileRepository;
import ru.pobopo.smartthing.model.gateway.dashboard.*;
import ru.pobopo.smartthing.gateway.service.dashboard.DashboardGroupWorker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final FileRepository<DashboardGroup> repository;
    private final DashboardGroupWorkerFactory workerFactory;
    private final Map<UUID, DashboardGroupWorker> workers = new ConcurrentHashMap<>();

    public Collection<DashboardGroup> getGroups() {
        return repository.getAll();
    }

    public DashboardGroup createGroup(DashboardGroup group) throws ValidationException {
        Objects.requireNonNull(group);
        if (group.getDevice() == null) {
            throw new ValidationException("Device can't be null!");
        }

        Optional<DashboardGroup> existingGroup = repository.getAll().stream().filter(g -> g.getDevice().equals(group.getDevice())).findFirst();
        if (existingGroup.isPresent()) {
            throw new ValidationException("Group for this device already exists, id=" + existingGroup.get().getId());
        }

        Set<UUID> ids = repository.getAll().stream().map(DashboardGroup::getId).collect(Collectors.toSet());
        UUID uuid = UUID.randomUUID();
        while(ids.contains(uuid)) {
            uuid = UUID.randomUUID();
        }
        group.setId(uuid);
        repository.add(group);

        try {
            startGroupWorker(group);
            log.info("Created new group {}", group);
            return group;
        } catch (Exception e) {
            repository.delete(group.getId());
            throw e;
        }
    }

    public DashboardGroup updateGroup(DashboardGroup group) throws ValidationException {
        Optional<DashboardGroup> optionalGroup = repository.findById(group.getId());
        if (optionalGroup.isEmpty()) {
            throw new ValidationException("Can't find group by id " + group.getId());
        }
        log.info("Updating group {}", group);

        DashboardGroup updatedGroup = optionalGroup.get().toBuilder()
                .device(group.getDevice())
                .config(group.getConfig())
                .observables(group.getObservables().stream()
                        .filter((o) -> StringUtils.isNotBlank(o.getName()) && o.getType() != null)
                        .toList())
                .actions(group.getActions())
                .build();
        repository.update(updatedGroup);

        if (workers.containsKey(group.getId())) {
            log.info("Updating group worker");
            workers.get(group.getId()).updateGroup(group);
            workers.get(group.getId()).interrupt();
        } else {
            log.error("Group worker missing!");
        }

        log.info("Group {} was updated", group.getId());
        return updatedGroup;
    }

    public void deleteGroup(UUID id) throws ValidationException, InterruptedException {
        if (id == null) {
            throw new ValidationException("Group's id is missing!");
        }

        Optional<DashboardGroup> foundGroup = repository.findById(id);
        if (foundGroup.isEmpty()) {
            throw new ValidationException("Can't find group by id " + id);
        }

        DashboardGroupWorker worker = workers.get(id);
        if (worker != null) {
            log.info("Trying to stop group worker");
            worker.setRunning(false);
            worker.interrupt();
            worker.join();
            workers.remove(id);
            log.info("Worker stopped and removed");
        }

        repository.delete(id);
        log.info("Group {} was deleted", id);
    }

    public List<DashboardGroupValues> getValues() {
        if (workers.isEmpty()) {
            return List.of();
        }
        return workers.values().stream().map((w) -> new DashboardGroupValues(w.getGroup(), observablesMapToList(w.getValues()))).toList();
    }

    public List<DashboardObservableValues> getGroupValues(UUID id) {
        Objects.requireNonNull(id);

        if (!workers.containsKey(id)) {
            throw new ValidationException("Group with id=" + id + " not found");
        }

        return observablesMapToList(workers.get(id).getValues());
    }

    public void updateValues(UUID id) {
        Objects.requireNonNull(id);

        if (!workers.containsKey(id)) {
            return;
        }
        workers.get(id).update();
    }

    private List<DashboardObservableValues> observablesMapToList(Map<DashboardObservable, Deque<DashboardObservableValue>> values) {
        List<DashboardObservableValues> result = new ArrayList<>();

        for(Map.Entry<DashboardObservable, Deque<DashboardObservableValue>> entry: values.entrySet()) {
            result.add(new DashboardObservableValues(
                    entry.getKey(),
                    entry.getValue())
            );
        }

        return result;
    }

    private void startGroupWorker(DashboardGroup group) {
        if (workers.containsKey(group.getId())) {
            throw new IllegalStateException("Worker for group " + group.getDevice().getName() + " already exists");
        }

        DashboardGroupWorker worker = workerFactory.create(group);
        try {
            log.info("Starting new dashboard worker for group {}", group);
            worker.start();
            workers.put(group.getId(), worker);
        } catch (Exception e) {
            log.error("Failed to start group worker", e);
        }
    }

    @EventListener
    public void startWorkers(ApplicationReadyEvent event) {
        log.info("Trying to start groups workers");
        Collection<DashboardGroup> groups = repository.getAll();
        if (groups.isEmpty()) {
            log.info("Empty groups, nothing to start");
        } else {
            for (DashboardGroup group: groups) {
                startGroupWorker(group);
            }
        }
    }
}
