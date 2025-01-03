package ru.pobopo.smartthing.gateway.service.device;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.pobopo.smartthing.gateway.cache.ConcurrentSetCache;
import ru.pobopo.smartthing.gateway.service.job.BackgroundJob;
import ru.pobopo.smartthing.model.device.DeviceInfo;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.pobopo.smartthing.gateway.config.StompMessagingConfig.DEVICES_TOPIC;

@Component
@Slf4j
@RequiredArgsConstructor
public class DevicesSearchService implements BackgroundJob {
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${device.search.group}")
    private String searchGroup;
    @Value("${device.search.port}")
    private int searchPort;

    private final AtomicBoolean socketConnected = new AtomicBoolean(false);
    private final ConcurrentSetCache<DeviceInfo> cache = new ConcurrentSetCache<>(5, ChronoUnit.SECONDS, this::onDeviceFound, this::onDeviceLost);

    public boolean isSearchEnabled() {
        return socketConnected.get();
    }

    @NonNull
    public Set<DeviceInfo> getRecentFoundDevices() {
        return cache.getValues();
    }

    @Scheduled(fixedDelayString = "5000")
    public void cacheEvict() {
        cache.evictItems();
    }

    private void onDeviceFound(DeviceInfo info) {
        messagingTemplate.convertAndSend(DEVICES_TOPIC + "/found", info);
    }

    private void onDeviceLost(DeviceInfo info) {
        messagingTemplate.convertAndSend(DEVICES_TOPIC + "/lost", info);
    }

    @Override
    public void run() {
        log.info("Device search job started");
        try {
            search();
        } catch (IOException exception) {
            log.error("Search job stopped!", exception);
            throw new RuntimeException(exception);
        }
    }

    private void search() throws IOException {
        SocketAddress address = new InetSocketAddress(InetAddress.getByName(searchGroup), searchPort);
        MulticastSocket multicastSocket = new MulticastSocket(address);
        byte[] buf = new byte[4096];

        try {
            multicastSocket.joinGroup(address, multicastSocket.getNetworkInterface());
            this.socketConnected.lazySet(true);
            while (!Thread.interrupted()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                multicastSocket.receive(packet);

                String message = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8
                );

                DeviceInfo deviceInfo = DeviceInfo.fromMulticastMessage(message);

                if (deviceInfo != null) {
                    cache.put(deviceInfo);
                } else {
                    log.debug("Can't build device info from {}", message);
                }
            }
        } catch (Exception exception) {
            log.error("Search job failed", exception);
            this.socketConnected.lazySet(false);
            throw new RuntimeException(exception);
        } finally {
            multicastSocket.leaveGroup(address, multicastSocket.getNetworkInterface());
            multicastSocket.close();
        }
    }

}
