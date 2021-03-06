package com.boclips.eventbus.infrastructure;

import com.boclips.eventbus.ConflictingSubscriberException;
import com.boclips.eventbus.EventBus;
import com.boclips.eventbus.EventHandler;
import com.boclips.eventbus.config.BoclipsEventsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.threeten.bp.Duration;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@ConditionalOnMissingBean(EventBus.class)
public class PubSubEventBus extends AbstractEventBus {
    private final Logger logger = Logger.getLogger(PubSubEventBus.class.getName());
    private final String projectId;
    private final String consumerGroup;
    private final ObjectMapper objectMapper;
    private final Map<String, Subscriber> subscriberByTopic = new HashMap<>();
    private final Map<String, Publisher> publisherByTopic = new HashMap<>();

    private final ExecutorProvider executorProvider = InstantiatingExecutorProvider.newBuilder()
            .setExecutorThreadCount(1)
            .setThreadFactory(threadFactory("PubSub-executor"))
            .build();

    private final FlowControlSettings flowControlSettings = FlowControlSettings.newBuilder()
            .setMaxOutstandingElementCount(10L)
            .setMaxOutstandingRequestBytes(1024L * 1024L) // 1MB
            .build();

    private final BatchingSettings publisherBatchingSettings = BatchingSettings.newBuilder()
            .setElementCountThreshold(200L)
            .setRequestByteThreshold(10000L)
            .setDelayThreshold(Duration.ofSeconds(1))
            .build();

    public PubSubEventBus(BoclipsEventsProperties properties) {
        validateConfig(properties);

        this.objectMapper = ObjectMapperProvider.get();
        this.projectId = properties.getProject();
        this.consumerGroup = properties.getConsumerGroup();
    }

    private static ThreadFactory threadFactory(String name) {
        return new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat(name + "-%d")
                .build();
    }

    @Override
    public <T> void doSubscribe(String topicName, Class<T> eventType, EventHandler<? super T> eventHandler) {
        subscriberByTopic.computeIfPresent(topicName, (cls, subscriber) -> {
            throw new ConflictingSubscriberException("There already is a subscription for " + eventType.getSimpleName());
        });

        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, topicName + "." + consumerGroup);

        try {
            createSubscription(subscriptionName, topicName);
        } catch (IOException e) {
            throw new RuntimeException("Could not create subscription", e);
        }

        MessageReceiver receiver =
                (message, consumer) -> {
                    T payload = null;
                    try {
                        payload = objectMapper.readValue(message.getData().toStringUtf8(), eventType);
                        eventHandler.handle(payload);
                    } catch (Exception e) {
                        logger.warning("Error handling message from " + subscriptionName.toString() + ": " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        String payloadMessage = payload != null ? payload.toString() : "";
                        logger.info("Ack'ing message for " + subscriptionName.toString() + "with payload: " + payloadMessage);
                        consumer.ack();
                    }
                };

        Subscriber subscriber = Subscriber
                .newBuilder(subscriptionName, receiver)
                .setExecutorProvider(executorProvider)
                .setParallelPullCount(1)
                .setFlowControlSettings(flowControlSettings)
                .build();

        subscriberByTopic.put(topicName, subscriber);

        subscriber.startAsync().awaitRunning();
        logger.info(String.format("Subscribed to %s", topicName));
    }

    @Override
    protected void doPublish(Iterable<?> events, String topicName) {
        logger.fine("Obtaining publisher for " + topicName);
        Publisher publisher = getPublisherFor(topicName);
        logger.fine("Obtained publisher for " + topicName);
        try {
            for (Object event : events) {
                logger.fine("Serializing event...");
                byte[] eventBytes = objectMapper.writeValueAsBytes(event);
                ByteString eventByteString = ByteString.copyFrom(eventBytes);
                PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(eventByteString).build();
                logger.fine("Serialized event. Publishing...");
                publisher.publish(pubsubMessage);
                logger.fine("Published");
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to publish a " + topicName + " event", e);
        }
        logger.fine("Done publishing batch");
    }


    @Override
    public void doUnsubscribe(String topicName) {
        subscriberByTopic.remove(topicName);
    }

    private synchronized Publisher getPublisherFor(String topicName) {
        return publisherByTopic.computeIfAbsent(topicName, key -> {
            try {
                TopicName topic = createTopicIfDoesNotExist(topicName);
                return Publisher
                        .newBuilder(topic)
                        .setBatchingSettings(publisherBatchingSettings)
                        .build();
            } catch (IOException e) {
                throw new IllegalStateException(String.format("Failed to create publisher for %s", topicName));
            }
        });
    }

    private void createSubscription(ProjectSubscriptionName subscriptionName, String topicId) throws IOException {
        TopicName topicName = createTopicIfDoesNotExist(topicId);

        try (SubscriptionAdminClient subscriptionAdmin = subscriptionAdminClient()) {
            if (subscriptionDoesNotExist(subscriptionAdmin, subscriptionName)) {
                Subscription subscription = subscriptionAdmin.createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(), 0);
                logger.info(String.format("Created subscription %s", subscription.getName()));
            }
        }
    }

    public TopicAdminClient topicAdminClient() throws IOException {
        TopicAdminSettings topicAdminSettings = TopicAdminSettings
                .newBuilder()
                .build();
        return TopicAdminClient.create(topicAdminSettings);
    }

    public SubscriptionAdminClient subscriptionAdminClient() throws IOException {
        SubscriptionAdminSettings subscriptionAdminSettings = SubscriptionAdminSettings
                .newBuilder()
                .build();
        return SubscriptionAdminClient.create(subscriptionAdminSettings);
    }

    private TopicName createTopicIfDoesNotExist(String topicId) throws IOException {
        TopicName topicName = TopicName.of(projectId, topicId);
        try (TopicAdminClient topicAdmin = topicAdminClient()) {
            if (topicDoesNotExist(topicAdmin, topicName)) {
                Topic topic = topicAdmin.createTopic(topicName);
                logger.info(String.format("Created topic %s", topic.getName()));
            }
        }
        return topicName;
    }

    private boolean topicDoesNotExist(TopicAdminClient topicAdminClient, TopicName topicName) {
        try {
            logger.fine("Checking if topic " + topicName + " exists");
            topicAdminClient.getTopic(topicName);
            return false;
        } catch (NotFoundException e) {
            return true;
        }
    }

    private boolean subscriptionDoesNotExist(SubscriptionAdminClient subscriptionAdminClient, ProjectSubscriptionName subscriptionName) {
        try {
            subscriptionAdminClient.getSubscription(subscriptionName);
            return false;
        } catch (NotFoundException e) {
            return true;
        }
    }

    private static void validateConfig(BoclipsEventsProperties properties) {
        String consumerGroup = properties.getConsumerGroup();
        if (consumerGroup == null || consumerGroup.isEmpty()) {
            throw new IllegalArgumentException("PUBSUB_CONSUMER_GROUP must be defined");
        }

        String projectId = properties.getProject();
        if (projectId == null || projectId.isEmpty()) {
            throw new IllegalArgumentException("PUBSUB_PROJECT must be defined");
        }
    }

    @PreDestroy
    public void closeSubscriptionsAndPublishers() {
        subscriberByTopic.forEach((key, subscriber) -> {
            try {
                subscriber.stopAsync().awaitTerminated();
                logger.info(String.format("Closed subscription for %s [%s]", key, subscriber.state()));
            } catch (Exception e) {
                logger.log(Level.SEVERE, e, () -> "Error shutting down subscriber for " + key);
            }
        });
        publisherByTopic.forEach((key, publisher) -> {
            try {
                publisher.shutdown();
                logger.info(String.format("Shutdown publisher for %s", key));
            } catch (Exception e) {
                logger.log(Level.SEVERE, e, () -> "Error shutting down publisher for " + key);
            }

        });
    }
}
