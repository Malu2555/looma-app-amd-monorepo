package com.divric.looma_assistant.actor.telemetry;

import akka.actor.AbstractActor;
import akka.actor.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * TelemetryBridgeActor — receives telemetry events from child workers
 * as they complete tasks, logging execution time and estimated token savings.
 * <p>
 * This actor acts as a centralized metrics collector for observability.
 * In a production system, events could be forwarded to Prometheus / Grafana / CloudWatch.
 */
public class TelemetryBridgeActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBridgeActor.class);

    // ── Inbound message ───────────────────────────────────────────────

    /**
     * A telemetry event emitted by a worker after processing a task.
     */
    public static final class TelemetryEvent {
        public final String workerName;
        public final long elapsedMs;
        public final int estimatedTokens;

        public TelemetryEvent(String workerName, long elapsedMs, int estimatedTokens) {
            this.workerName = workerName;
            this.elapsedMs = elapsedMs;
            this.estimatedTokens = estimatedTokens;
        }
    }

    /**
     * Request to dump all accumulated telemetry (for debugging / admin endpoints).
     */
    public static final class DumpTelemetry {
        public DumpTelemetry() {
        }
    }

    // ── Actor state ───────────────────────────────────────────────────

    private final List<TelemetryEvent> events = new ArrayList<TelemetryEvent>();

    // ── Props factory ─────────────────────────────────────────────────

    public static Props props() {
        return Props.create(TelemetryBridgeActor.class, TelemetryBridgeActor::new);
    }

    // ── Message handling ──────────────────────────────────────────────

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TelemetryEvent.class, this::onTelemetryEvent)
                .match(DumpTelemetry.class, this::onDumpTelemetry)
                .build();
    }

    /**
     * Records a telemetry event and logs it.
     */
    private void onTelemetryEvent(TelemetryEvent event) {
        events.add(event);
        log.info("Telemetry [{}]: elapsed={}ms, estimatedTokens={}",
                event.workerName, event.elapsedMs, event.estimatedTokens);
    }

    /**
     * Logs and returns all accumulated events (for diagnostics).
     */
    private void onDumpTelemetry(DumpTelemetry msg) {
        log.info("Telemetry dump: {} events recorded", events.size());
        for (TelemetryEvent e : events) {
            log.info("  {} -> {}ms, {} tokens", e.workerName, e.elapsedMs, e.estimatedTokens);
        }
        getSender().tell(new ArrayList<TelemetryEvent>(events), getSelf());
    }
}