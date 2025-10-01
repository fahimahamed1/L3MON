package com.etechd.l3mon.core.config;

public final class Config {

    private Config() {
        // Prevent instantiation
    }

    public static final String SERVER_HOST = "http://192.168.0.108:22533";
    public static final String HOME_PAGE_URL = "https://google.com";
    public static final boolean SERVER_USE_HTTPS = SERVER_HOST.startsWith("https");
    public static final String SOCKET_PATH = "/socket.io";

    public static String getSocketEndpoint() {
        return SERVER_HOST;
    }
}
