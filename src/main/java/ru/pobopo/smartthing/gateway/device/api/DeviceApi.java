package ru.pobopo.smartthing.gateway.device.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import ru.pobopo.smartthing.model.DeviceInfo;
import ru.pobopo.smartthing.model.InternalHttpResponse;
import ru.pobopo.smartthing.model.stomp.DeviceRequest;

@Slf4j
public abstract class DeviceApi {
    public abstract boolean accept(DeviceInfo deviceInfo);

    public abstract InternalHttpResponse health(DeviceInfo info);

    public abstract InternalHttpResponse getInfo(DeviceInfo info);

    public InternalHttpResponse getActions(DeviceInfo info) {
        log.info("Calling default getActions method");
        return new InternalHttpResponse(HttpStatus.OK.value(), "[]", null);
    }
    public InternalHttpResponse callAction(DeviceInfo info, String action) {
        log.info("Calling default callAction method");
        return new InternalHttpResponse(HttpStatus.BAD_REQUEST.value(), "There is no action handler", null);
    }

    public InternalHttpResponse getSensors(DeviceInfo info) {
        log.info("Calling default getSensors method");
        return new InternalHttpResponse(HttpStatus.OK.value(), "[]", null);
    }

    public InternalHttpResponse getStates(DeviceInfo info) {
        log.info("Calling default getStates method");
        return new InternalHttpResponse(HttpStatus.OK.value(), "[]", null);
    }
}
