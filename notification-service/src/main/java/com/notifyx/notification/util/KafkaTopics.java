package com.notifyx.notification.util;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String NOTIFICATION_EVENTS_TOPIC = "notification-events-topic";
    public static final String NOTIFICATION_STATUS_TOPIC = "notification-status-topic";
    public static final String NOTIFICATION_DLQ_TOPIC = "notification-dlq-topic";
}