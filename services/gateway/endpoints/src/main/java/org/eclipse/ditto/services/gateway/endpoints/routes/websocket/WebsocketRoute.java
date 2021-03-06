/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractUpgradeToWebSocket;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.ResponsePublished;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.actors.CommandSubscriber;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.EventStream;
import akka.event.Logging;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.UpgradeToWebSocket;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.Attributes;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Builder for creating Akka HTTP routes for {@code /ws}.
 */
public final class WebsocketRoute {

    private static final String START_SEND_EVENTS = "START-SEND-EVENTS";
    private static final String STOP_SEND_EVENTS = "STOP-SEND-EVENTS";

    private static final String START_SEND_MESSAGES = "START-SEND-MESSAGES";
    private static final String STOP_SEND_MESSAGES = "STOP-SEND-MESSAGES";

    private static final String START_SEND_LIVE_COMMANDS = "START-SEND-LIVE-COMMANDS";
    private static final String STOP_SEND_LIVE_COMMANDS = "STOP-SEND-LIVE-COMMANDS";

    private static final String START_SEND_LIVE_EVENTS = "START-SEND-LIVE-EVENTS";
    private static final String STOP_SEND_LIVE_EVENTS = "STOP-SEND-LIVE-EVENTS";

    /**
     * The backend sends the protocol message above suffixed by ":ACK" when the subscription was created. E.g.: {@code
     * START-SEND-EVENTS:ACK}
     */
    private static final String PROTOCOL_CMD_ACK_SUFFIX = ":ACK";

    private static final String STREAMING_TYPE_WS = "WS";

    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_NAMESPACES = "namespaces";

    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketRoute.class);

    private final ActorRef streamingActor;
    private final int subscriberBackpressureQueueSize;
    private final int publisherBackpressureBufferSize;

    private final EventStream eventStream;

    /**
     * Constructs the {@code /ws} route builder.
     *
     * @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param subscriberBackpressureQueueSize the max queue size of how many inflight Commands a single Websocket client
     * can have.
     * @param publisherBackpressureBufferSize the max buffer size of how many outstanding CommandResponses and Events a
     * single Websocket client can have - additionally incoming CommandResponses and Events are dropped if this size is
     * @param eventStream eventStream used to publish events within the actor system
     */
    public WebsocketRoute(final ActorRef streamingActor,
            final int subscriberBackpressureQueueSize,
            final int publisherBackpressureBufferSize,
            final EventStream eventStream) {
        this.streamingActor = streamingActor;
        this.subscriberBackpressureQueueSize = subscriberBackpressureQueueSize;
        this.publisherBackpressureBufferSize = publisherBackpressureBufferSize;
        this.eventStream = eventStream;
    }

    /**
     * Builds the {@code /ws} route.
     *
     * @return the {@code /ws} route.
     */
    public Route buildWebsocketRoute(final Integer version, final String correlationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders,
            final ProtocolAdapter chosenProtocolAdapter) {

        return extractUpgradeToWebSocket(upgradeToWebSocket ->
                complete(
                        createWebsocket(upgradeToWebSocket, version, correlationId, connectionAuthContext,
                                additionalHeaders, chosenProtocolAdapter)
                )
        );
    }

    private HttpResponse createWebsocket(final UpgradeToWebSocket upgradeToWebSocket, final Integer version,
            final String connectionCorrelationId, final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders, final ProtocolAdapter adapter) {

        LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger ->
                logger.info("Creating WebSocket for connection authContext: <{}>", connectionAuthContext));

        // build Sink and Source in order to support rpc style patterns as well as server push:
        return upgradeToWebSocket.handleMessagesWith(
                createSink(version, connectionCorrelationId, connectionAuthContext, additionalHeaders, adapter),
                createSource(connectionCorrelationId, adapter));
    }

    private Sink<Message, NotUsed> createSink(final Integer version, final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter) {
        return Flow.<Message>create()
                .filter(Message::isText)
                .map(Message::asTextMessage)
                .map(textMsg -> {
                    if (textMsg.isStrict()) {
                        return Source.single(textMsg.getStrictText());
                    } else {
                        return textMsg.getStreamedText();
                    }
                })
                .flatMapConcat(textMsg -> textMsg.<String>fold("", (str1, str2) -> str1 + str2))
                .via(Flow.fromFunction(result -> {
                    LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger ->
                            logger.debug("Received incoming WebSocket message: {}", result));
                    return result;
                }))
                .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(),
                        Logging.WarningLevel()))
                .filter(strictText -> processProtocolMessage(connectionAuthContext, connectionCorrelationId,
                        strictText))
                .map(buildSignal(version, connectionCorrelationId, connectionAuthContext, additionalHeaders, adapter))
                .to(Sink.actorSubscriber(
                        CommandSubscriber.props(streamingActor, subscriberBackpressureQueueSize, eventStream)));

    }

    private boolean processProtocolMessage(final AuthorizationContext authContext, final String connectionCorrelationId,
            final String protocolMessage) {
        Object messageToTellStreamingActor;
        switch (protocolMessage) {
            case START_SEND_EVENTS:
                messageToTellStreamingActor =
                        new StartStreaming(StreamingType.EVENTS, connectionCorrelationId, authContext,
                                Collections.emptyList(), null);
                break;
            case STOP_SEND_EVENTS:
                messageToTellStreamingActor = new StopStreaming(StreamingType.EVENTS, connectionCorrelationId);
                break;
            case START_SEND_MESSAGES:
                messageToTellStreamingActor =
                        new StartStreaming(StreamingType.MESSAGES, connectionCorrelationId, authContext,
                                Collections.emptyList(),null);
                break;
            case STOP_SEND_MESSAGES:
                messageToTellStreamingActor = new StopStreaming(StreamingType.MESSAGES, connectionCorrelationId);
                break;
            case START_SEND_LIVE_COMMANDS:
                messageToTellStreamingActor = new StartStreaming(StreamingType.LIVE_COMMANDS, connectionCorrelationId,
                        authContext, Collections.emptyList(),null);
                break;
            case STOP_SEND_LIVE_COMMANDS:
                messageToTellStreamingActor = new StopStreaming(StreamingType.LIVE_COMMANDS, connectionCorrelationId);
                break;
            case START_SEND_LIVE_EVENTS:
                messageToTellStreamingActor =
                        new StartStreaming(StreamingType.LIVE_EVENTS, connectionCorrelationId, authContext,
                                Collections.emptyList(),null);
                break;
            case STOP_SEND_LIVE_EVENTS:
                messageToTellStreamingActor = new StopStreaming(StreamingType.LIVE_EVENTS, connectionCorrelationId);
                break;
            default:
                messageToTellStreamingActor = null;
        }

        final Map<String, String> params = determineParams(protocolMessage);
        final List<String> namespaces = Optional.ofNullable(params.get(PARAM_NAMESPACES))
                .map(ids -> ids.split(","))
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
        final String filter = params.get(PARAM_FILTER);

        if (messageToTellStreamingActor == null && protocolMessage.startsWith(START_SEND_EVENTS + "?")) {
            messageToTellStreamingActor = new StartStreaming(StreamingType.EVENTS, connectionCorrelationId,
                            authContext, namespaces, filter);
        } else if (messageToTellStreamingActor == null && protocolMessage.startsWith(START_SEND_LIVE_EVENTS + "?")) {
            messageToTellStreamingActor = new StartStreaming(StreamingType.LIVE_EVENTS, connectionCorrelationId,
                    authContext, namespaces, filter);
        } else if (messageToTellStreamingActor == null && protocolMessage.startsWith(START_SEND_LIVE_COMMANDS + "?")) {
            messageToTellStreamingActor = new StartStreaming(StreamingType.LIVE_COMMANDS, connectionCorrelationId,
                    authContext, namespaces, null);
        } else if (messageToTellStreamingActor == null && protocolMessage.startsWith(START_SEND_MESSAGES + "?")) {
            messageToTellStreamingActor = new StartStreaming(StreamingType.MESSAGES, connectionCorrelationId,
                    authContext, namespaces, null);
        }

        if (messageToTellStreamingActor != null) {
            streamingActor.tell(messageToTellStreamingActor, null);
            return false;
        }
        // let all other messages pass:
        return true;
    }

    /**
     * Parses the passed {@code protocolMessage} for an optional parameters string (e.g. containing a "filter") and
     * creating a Map from it.
     *
     * @param protocolMessage the protocolMessage containing parameters, e.g.: {@code START-SEND-EVENTS?filter=eq(foo,1)}
     * @return the map containing the resolved params
     */
    private Map<String, String> determineParams(final String protocolMessage) {
        if (protocolMessage.contains("?")) {
            final String parametersString = protocolMessage.split("\\?", 2)[1];
            return Arrays.stream(parametersString.split("&"))
                    .map(paramWithValue -> paramWithValue.split("=", 2))
                    .collect(Collectors.toMap(pv -> urlDecode(pv[0]), pv -> urlDecode(pv[1])));
        } else {
            return Collections.emptyMap();
        }
    }

    private static String urlDecode(final String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            return URLDecoder.decode(value);
        }
    }

    private Source<Message, NotUsed> createSource(final String connectionCorrelationId, final ProtocolAdapter adapter) {
        return Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                EventAndResponsePublisher.props(publisherBackpressureBufferSize))
                .mapMaterializedValue(actorRef -> {
                    streamingActor.tell(new Connect(actorRef, connectionCorrelationId, STREAMING_TYPE_WS), null);
                    return NotUsed.getInstance();
                })
                .map(this::publishResponsePublishedEvent)
                .map(jsonifiableToString(adapter))
                .via(Flow.fromFunction(result -> {
                    LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger ->
                            logger.debug("Sending outgoing WebSocket message: {}", result));
                    return result;
                }))
                .map(TextMessage::create);
    }

    private Jsonifiable.WithPredicate<JsonObject, JsonField> publishResponsePublishedEvent(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        if (jsonifiable instanceof CommandResponse) {
            // only create ResponsePublished for CommandResponses, not for Events with the same correlationId
            ((WithDittoHeaders) jsonifiable).getDittoHeaders()
                    .getCorrelationId()
                    .map(ResponsePublished::new)
                    .ifPresent(eventStream::publish);
        }
        return jsonifiable;
    }

    private Function<String, Signal> buildSignal(final Integer version, final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter) {
        return cmdString -> {
            final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.forInt(version)
                    .orElseThrow(() -> CommandNotSupportedException.newBuilder(version).build());

            // initial internal header values
            final DittoHeaders initialInternalHeaders = DittoHeaders.newBuilder()
                    .schemaVersion(jsonSchemaVersion)
                    .authorizationContext(connectionAuthContext)
                    .correlationId(connectionCorrelationId) // for logging
                    .origin(connectionCorrelationId)
                    .build();

            if (cmdString.isEmpty()) {
                final RuntimeException cause = new IllegalArgumentException("Empty json.");
                throw new DittoJsonException(cause, initialInternalHeaders);
            }

            final JsonifiableAdaptable jsonifiableAdaptable = wrapJsonRuntimeException(cmdString,
                    DittoHeaders.empty(), // unused
                    (s, unused) -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(s)));

            final Signal<? extends Signal> signal;
            try {
                signal = adapter.fromAdaptable(jsonifiableAdaptable);
            } catch (final DittoRuntimeException e) {
                throw e.setDittoHeaders(e.getDittoHeaders().toBuilder().origin(connectionCorrelationId).build());
            }

            final DittoHeadersBuilder internalHeadersBuilder = DittoHeaders.newBuilder();

            LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger -> {
                logger.debug("WebSocket message has been converted to signal <{}>.", signal);
                final DittoHeaders signalHeaders = signal.getDittoHeaders();

                // add initial internal header values
                logger.trace("Adding initialInternalHeaders: <{}>.", initialInternalHeaders);
                internalHeadersBuilder.putHeaders(initialInternalHeaders);
                // add headers given by parent route first so that protocol message may override them
                logger.trace("Adding additionalHeaders: <{}>.", additionalHeaders);
                internalHeadersBuilder.putHeaders(additionalHeaders);
                // add any headers from protocol adapter to internal headers
                logger.trace("Adding signalHeaders: <{}>.", signalHeaders);
                internalHeadersBuilder.putHeaders(signalHeaders);
                // generate correlation ID if it is not set in protocol message
                if (!signalHeaders.getCorrelationId().isPresent()) {
                    final String correlationId = UUID.randomUUID().toString();
                    logger.trace("Adding generated correlationId: <{}>.", correlationId);
                    internalHeadersBuilder.correlationId(correlationId);
                }
                logger.debug("Generated internalHeaders are: <{}>.", internalHeadersBuilder);
            });

            return signal.setDittoHeaders(internalHeadersBuilder.build());
        };
    }

    private static Function<Jsonifiable.WithPredicate<JsonObject, JsonField>, String> jsonifiableToString(
            final ProtocolAdapter adapter) {
        return jsonifiable -> {
            if (jsonifiable instanceof StreamingAck) {
                return streamingAckToString((StreamingAck) jsonifiable);
            }

            final Adaptable adaptable;
            if (jsonifiable instanceof WithDittoHeaders
                    && ((WithDittoHeaders) jsonifiable).getDittoHeaders().getChannel().isPresent()) {
                // if channel was present in headers, use that one:
                final TopicPath.Channel channel =
                        TopicPath.Channel.forName(((WithDittoHeaders) jsonifiable).getDittoHeaders().getChannel().get())
                                .orElse(TopicPath.Channel.TWIN);
                adaptable = jsonifiableToAdaptable(jsonifiable, channel, adapter);
            } else if (jsonifiable instanceof Signal && isLiveSignal((Signal<?>) jsonifiable)) {
                adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.LIVE, adapter);
            } else {
                adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.TWIN, adapter);
            }

            final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
            return jsonifiableAdaptable.toJsonString();
        };
    }

    private static String streamingAckToString(final StreamingAck streamingAck) {
        final StreamingType streamingType = streamingAck.getStreamingType();
        final boolean subscribed = streamingAck.isSubscribed();
        final String protocolMessage;
        switch (streamingType) {
            case EVENTS:
                protocolMessage = subscribed ? START_SEND_EVENTS : STOP_SEND_EVENTS;
                break;
            case MESSAGES:
                protocolMessage = subscribed ? START_SEND_MESSAGES : STOP_SEND_MESSAGES;
                break;
            case LIVE_COMMANDS:
                protocolMessage = subscribed ? START_SEND_LIVE_COMMANDS : STOP_SEND_LIVE_COMMANDS;
                break;
            case LIVE_EVENTS:
                protocolMessage = subscribed ? START_SEND_LIVE_EVENTS : STOP_SEND_LIVE_EVENTS;
                break;
            default:
                throw new IllegalArgumentException("Unknown streamingType: " + streamingType);
        }
        return protocolMessage + PROTOCOL_CMD_ACK_SUFFIX;
    }

    private static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    private static Adaptable jsonifiableToAdaptable(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final TopicPath.Channel channel, final ProtocolAdapter adapter) {
        final Adaptable adaptable;
        if (jsonifiable instanceof Command) {
            adaptable = adapter.toAdaptable((Command) jsonifiable, channel);
        } else if (jsonifiable instanceof Event) {
            adaptable = adapter.toAdaptable((Event) jsonifiable, channel);
        } else if (jsonifiable instanceof CommandResponse) {
            adaptable = adapter.toAdaptable((CommandResponse) jsonifiable, channel);
        } else if (jsonifiable instanceof DittoRuntimeException) {
            final DittoHeaders enhancedHeaders = ((DittoRuntimeException) jsonifiable).getDittoHeaders().toBuilder()
                    .channel(channel.getName())
                    .build();
            ThingErrorResponse errorResponse;
            try {
                errorResponse = ThingErrorResponse.of(MessageHeaders.of(enhancedHeaders).getThingId(),
                        (DittoRuntimeException) jsonifiable, enhancedHeaders);
            } catch (final IllegalStateException | IllegalArgumentException | DittoRuntimeException e) {
                // thrown if headers did not contain the thing ID:
                errorResponse = ThingErrorResponse.of((DittoRuntimeException) jsonifiable, enhancedHeaders);
            }
            adaptable = adapter.toAdaptable(errorResponse, channel);
        } else {
            throw new IllegalArgumentException("Jsonifiable was neither Command nor CommandResponse nor"
                    + " Event nor DittoRuntimeException: " + jsonifiable.getClass().getSimpleName());
        }
        return adaptable;
    }

}
