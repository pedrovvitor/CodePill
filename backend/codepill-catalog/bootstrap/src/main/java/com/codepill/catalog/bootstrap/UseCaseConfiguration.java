package com.codepill.catalog.bootstrap;

import com.codepill.catalog.application.metrics.CatalogMetrics;
import com.codepill.catalog.application.port.out.DeletePillPort;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.SavePillPort;
import com.codepill.catalog.application.usecase.CreatePillUseCase;
import com.codepill.catalog.application.usecase.DeletePillUseCase;
import com.codepill.catalog.application.usecase.GetPillUseCase;
import com.codepill.catalog.application.usecase.ListPillsUseCase;
import com.codepill.catalog.application.usecase.PublishPillUseCase;
import com.codepill.catalog.application.usecase.UpdatePillUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the framework-agnostic use cases as beans so Spring can apply
 * transactions, method security and observations to them — the application
 * module itself stays free of Spring stereotypes. The ObservationRegistry lets
 * use cases enrich their @Observed span with codepill.* attributes
 * (OBSERVABILITY.md §2 rule 4).
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CatalogMetrics catalogMetrics(MeterRegistry meterRegistry) {
        return new CatalogMetrics(meterRegistry);
    }

    @Bean
    CreatePillUseCase createPillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                                        PillCachePort pillCachePort, CatalogMetrics metrics,
                                        ObservationRegistry observations) {
        return new CreatePillUseCase(loadPillPort, savePillPort, pillCachePort, metrics, observations);
    }

    @Bean
    GetPillUseCase getPillUseCase(LoadPillPort loadPillPort, PillCachePort pillCachePort,
                                  ObservationRegistry observations) {
        return new GetPillUseCase(loadPillPort, pillCachePort, observations);
    }

    @Bean
    ListPillsUseCase listPillsUseCase(LoadPillPort loadPillPort, PillCachePort pillCachePort,
                                      ObservationRegistry observations) {
        return new ListPillsUseCase(loadPillPort, pillCachePort, observations);
    }

    @Bean
    UpdatePillUseCase updatePillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                                        PillCachePort pillCachePort,
                                        ObservationRegistry observations) {
        return new UpdatePillUseCase(loadPillPort, savePillPort, pillCachePort, observations);
    }

    @Bean
    DeletePillUseCase deletePillUseCase(LoadPillPort loadPillPort, DeletePillPort deletePillPort,
                                        PillCachePort pillCachePort,
                                        ObservationRegistry observations) {
        return new DeletePillUseCase(loadPillPort, deletePillPort, pillCachePort, observations);
    }

    @Bean
    PublishPillUseCase publishPillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                                          PillCachePort pillCachePort, CatalogMetrics metrics,
                                          Clock clock, ObservationRegistry observations) {
        return new PublishPillUseCase(loadPillPort, savePillPort, pillCachePort, metrics, clock,
                observations);
    }
}
