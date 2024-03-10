package ru.pobopo.smart.thing.gateway.model;

import java.net.InetAddress;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;

@Data
@ToString
public class DeviceLoggerMessage {
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dateTime;
    private DeviceInfo device;
    private Level level;
    private String tag;
    private String message;
    private DeviceLogSource source;

    public static DeviceLoggerMessage parse(String address, String message) {
        DeviceLoggerMessage deviceLoggerMessage = new DeviceLoggerMessage();
        if (StringUtils.isBlank(message)) {
            return deviceLoggerMessage;
        }

        String[] splited = message.split("_&_*");
        if (splited.length != 4) {
            return deviceLoggerMessage;
        }

        deviceLoggerMessage.setDateTime(LocalDateTime.now());
        deviceLoggerMessage.setDevice(
            new DeviceInfo(address, splited[0])
        );
        String level = splited[1];
        if (StringUtils.isBlank(level)) {
            level = "INFO";
        }
        deviceLoggerMessage.setLevel(Level.valueOf(level));
        deviceLoggerMessage.setTag(splited[2]);
        deviceLoggerMessage.setMessage(splited[3].trim());
        return deviceLoggerMessage;
    }
}
