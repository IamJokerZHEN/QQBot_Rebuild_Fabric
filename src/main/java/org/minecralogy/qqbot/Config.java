package org.minecralogy.qqbot;

public class Config {
    public String uri;
    public String name;
    public String token;
    public int reconnect_interval;

    public int getReconnect_interval() {
        return reconnect_interval;
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    public String getUri() {
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}
