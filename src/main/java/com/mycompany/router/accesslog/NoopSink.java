package com.mycompany.router.accesslog;

public class NoopSink implements AccessLogSink {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public void publish(AccessLogEvent event) {
        // intentionally empty
    }
}
