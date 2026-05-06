package com.highloadinvest.banking.infrastructure.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * OpenTelemetry initialization for core-banking. Mirrors the api-gateway setup
 * but with a different default service name; the two services share W3C trace
 * propagation so api-gateway → core-banking calls show up as a single trace.
 */
object Telemetry {
    private val logger = LoggerFactory.getLogger(Telemetry::class.java)

    fun init(): OpenTelemetry {
        val disabled = System.getenv("OTEL_SDK_DISABLED")?.toBoolean() == true
        if (disabled) {
            logger.warn("OpenTelemetry SDK is disabled via OTEL_SDK_DISABLED=true; using no-op")
            return OpenTelemetry.noop()
        }

        val endpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") ?: "http://localhost:4317"
        val serviceName = System.getenv("OTEL_SERVICE_NAME") ?: "core-banking"
        val extraAttrs = System.getenv("OTEL_RESOURCE_ATTRIBUTES").orEmpty()

        logger.info("Initializing OpenTelemetry: service={} endpoint={}", serviceName, endpoint)

        val resourceBuilder = Resource.getDefault().toBuilder()
            .put(AttributeKey.stringKey("service.name"), serviceName)
            .put(AttributeKey.stringKey("service.version"), "0.1.0")
        extraAttrs.split(",").filter { it.contains("=") }.forEach { kv ->
            val (k, v) = kv.split("=", limit = 2)
            resourceBuilder.put(AttributeKey.stringKey(k.trim()), v.trim())
        }
        val resource = resourceBuilder.build()

        return try {
            val spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(5))
                .build()
            val tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build()

            val metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(5))
                .build()
            val meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(
                    PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofSeconds(15))
                        .build()
                )
                .setResource(resource)
                .build()

            val sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal()

            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("Flushing OpenTelemetry exporters")
                tracerProvider.shutdown()
                meterProvider.shutdown()
            })

            logger.info("OpenTelemetry initialized successfully")
            sdk
        } catch (e: Exception) {
            logger.warn("Failed to initialize OpenTelemetry, falling back to no-op: {}", e.message)
            OpenTelemetry.noop()
        }
    }
}
