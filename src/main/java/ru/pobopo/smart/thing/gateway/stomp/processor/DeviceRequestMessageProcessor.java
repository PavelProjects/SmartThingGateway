package ru.pobopo.smart.thing.gateway.stomp.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import ru.pobopo.smart.thing.gateway.exception.MissingValueException;
import ru.pobopo.smart.thing.gateway.service.DeviceApiService;
import ru.pobopo.smartthing.model.stomp.DeviceRequestMessage;
import ru.pobopo.smartthing.model.stomp.ResponseMessage;

@Slf4j
public class DeviceRequestMessageProcessor implements MessageProcessor {

    private final DeviceApiService apiService;

    @Autowired
    public DeviceRequestMessageProcessor(DeviceApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public ResponseMessage process(Object payload) throws Exception {
        DeviceRequestMessage request = (DeviceRequestMessage) payload;
        if (request.getId() == null) {
            throw new MissingValueException("Request id is missing!");
        }

        ResponseMessage response = new ResponseMessage();
        response.setRequestId(request.getId());
        response.setResponse(apiService.execute(request.getRequest()));

        return response;
    }

}
