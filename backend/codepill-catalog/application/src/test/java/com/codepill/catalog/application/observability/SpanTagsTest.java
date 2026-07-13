package com.codepill.catalog.application.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SpanTagsTest {

    ObservationRegistry registry;
    List<Observation.Context> stopped;

    @BeforeEach
    void setUp() {
        registry = ObservationRegistry.create();
        stopped = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }
        });
    }

    @Test
    void shouldAttachHighCardinalityAttribute_toCurrentObservation() {
        var observation = Observation.start("usecase", registry);
        try (var scope = observation.openScope()) {
            SpanTags.put(registry, "codepill.pill.id", "9f2c");
        } finally {
            observation.stop();
        }

        assertThat(stopped).hasSize(1);
        assertThat(stopped.getFirst().getHighCardinalityKeyValue("codepill.pill.id").getValue())
                .isEqualTo("9f2c");
    }

    @Test
    void shouldNoOp_whenNoObservationIsActive() {
        assertThatCode(() -> SpanTags.put(registry, "codepill.pill.id", "9f2c"))
                .doesNotThrowAnyException();
        assertThat(stopped).isEmpty();
    }

    @Test
    void shouldNoOp_onNoopRegistry() {
        assertThatCode(() -> SpanTags.put(ObservationRegistry.NOOP, "codepill.pill.id", "x"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNoOp_onNullRegistryOrValue() {
        assertThatCode(() -> SpanTags.put(null, "codepill.pill.id", "x"))
                .doesNotThrowAnyException();

        var observation = Observation.start("usecase", registry);
        try (var scope = observation.openScope()) {
            SpanTags.put(registry, "codepill.pill.id", null);
        } finally {
            observation.stop();
        }
        assertThat(stopped.getFirst().getHighCardinalityKeyValue("codepill.pill.id")).isNull();
    }
}
