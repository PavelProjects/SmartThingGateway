package ru.pobopo.smartthing.gateway.stomp;

import lombok.extern.slf4j.Slf4j;
import ru.pobopo.smartthing.gateway.stomp.processor.MessageProcessor;
import ru.pobopo.smartthing.model.stomp.BaseMessage;
import ru.pobopo.smartthing.model.stomp.MessageType;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MessageProcessorFactory {
    private final Map<MessageType, MessageProcessor> processorMap = new HashMap<>();

    public void addProcessor(MessageType type, MessageProcessor processor) {
        processorMap.put(type, processor);
    }

    public MessageProcessor getProcessor(BaseMessage message) {
        if (message.getType() == null) {
            log.warn("Message type is missing!");
            return null;
        }
        return processorMap.get(message.getType());
    }
}
