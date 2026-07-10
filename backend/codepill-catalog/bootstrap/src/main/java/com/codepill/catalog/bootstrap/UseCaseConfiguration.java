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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the framework-agnostic use cases as beans so Spring can apply
 * transactions, method security and observations to them — the application
 * module itself stays free of Spring stereotypes.
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
                                        PillCachePort pillCachePort, CatalogMetrics metrics) {
        return new CreatePillUseCase(loadPillPort, savePillPort, pillCachePort, metrics);
    }

    @Bean
    GetPillUseCase getPillUseCase(LoadPillPort loadPillPort, PillCachePort pillCachePort) {
        return new GetPillUseCase(loadPillPort, pillCachePort);
    }

    @Bean
    ListPillsUseCase listPillsUseCase(LoadPillPort loadPillPort, PillCachePort pillCachePort) {
        return new ListPillsUseCase(loadPillPort, pillCachePort);
    }

    @Bean
    UpdatePillUseCase updatePillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                                        PillCachePort pillCachePort) {
        return new UpdatePillUseCase(loadPillPort, savePillPort, pillCachePort);
    }

    @Bean
    DeletePillUseCase deletePillUseCase(LoadPillPort loadPillPort, DeletePillPort deletePillPort,
                                        PillCachePort pillCachePort) {
        return new DeletePillUseCase(loadPillPort, deletePillPort, pillCachePort);
    }

    @Bean
    PublishPillUseCase publishPillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                                          PillCachePort pillCachePort, CatalogMetrics metrics,
                                          Clock clock) {
        return new PublishPillUseCase(loadPillPort, savePillPort, pillCachePort, metrics, clock);
    }
}
