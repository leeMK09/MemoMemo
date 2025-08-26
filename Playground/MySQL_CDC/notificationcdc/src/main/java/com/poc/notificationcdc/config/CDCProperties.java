package com.poc.notificationcdc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cdc")
@Getter
@Setter
public class CDCProperties {

    private String host;
    private int port;
    private String username;
    private String password;
    private String schema;
    private String includeTable;
    private int serverId;
    private int connectTimeoutMs;
    private int reconnectDelayMs;

    private int queueCapacity;
    private int offerTimeoutMs;

    private String gtidSet;
    private String binlogFileName;
    private Long binlogPosition;

    private int consumeBatchSize;
    private int consumeFixedDelayMs;

    public boolean isBlankGtidSet() {
        return isBlank(gtidSet);
    }

    public boolean isBlankBinlogFileName() {
        return isBlank(binlogFileName);
    }

    public boolean isBlankBinlogPosition() {
        return binlogPosition == null;
    }

    private boolean isBlank(String value) {
        if (value == null) return true;

        return value.isBlank();
    }
}
