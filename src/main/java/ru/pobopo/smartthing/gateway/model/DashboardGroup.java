package ru.pobopo.smartthing.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.pobopo.smartthing.model.DeviceInfo;

import java.util.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardGroup {
    private UUID id;
    private DeviceInfo device;
    private List<DashboardObservable> observables;
    private DashboardGroupConfig config;
}
