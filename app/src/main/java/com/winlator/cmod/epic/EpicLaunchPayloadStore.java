package com.winlator.cmod.epic;

import com.winlator.cmod.core.EnvVars;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EpicLaunchPayloadStore {
    private static final long MAX_AGE_MS = 5 * 60 * 1000L;
    private static final Map<String, Payload> PAYLOADS = new ConcurrentHashMap<>();

    private EpicLaunchPayloadStore() {}

    public static String put(String commandLine, String workingDirectory, EnvVars environment) {
        long now = System.currentTimeMillis();
        PAYLOADS.entrySet().removeIf(entry -> now - entry.getValue().createdAt > MAX_AGE_MS);

        String token = UUID.randomUUID().toString();
        EnvVars copy = new EnvVars();
        if (environment != null) copy.putAll(environment);
        PAYLOADS.put(token, new Payload(commandLine, workingDirectory, copy, now));
        return token;
    }

    public static Payload take(String token) {
        if (token == null || token.isEmpty()) return null;
        Payload payload = PAYLOADS.remove(token);
        if (payload == null || System.currentTimeMillis() - payload.createdAt > MAX_AGE_MS) return null;
        return payload;
    }

    public static final class Payload {
        public final String commandLine;
        public final String workingDirectory;
        public final EnvVars environment;
        private final long createdAt;

        private Payload(String commandLine, String workingDirectory, EnvVars environment, long createdAt) {
            this.commandLine = commandLine == null ? "" : commandLine;
            this.workingDirectory = workingDirectory == null ? "" : workingDirectory;
            this.environment = environment;
            this.createdAt = createdAt;
        }
    }
}
