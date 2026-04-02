package com.ecm.core.asynctask;

import com.ecm.core.controller.OpsRecoveryController;
import com.ecm.core.controller.PreviewDiagnosticsController;
import com.ecm.core.controller.SearchController;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncTaskGovernanceConfiguration {

    @Bean
    AsyncTaskGovernanceProvider auditAsyncTaskGovernanceProvider(
        AuditExportAsyncTaskRegistry auditExportAsyncTaskRegistry
    ) {
        return new SimpleAsyncTaskGovernanceProvider(
            10,
            "audit",
            "Audit",
            () -> AsyncTaskSummaryAdapters.fromAuditExport(auditExportAsyncTaskRegistry.summary(null))
        );
    }

    @Bean
    AsyncTaskGovernanceProvider opsRecoveryAsyncTaskGovernanceProvider(
        OpsRecoveryController opsRecoveryController
    ) {
        return new SimpleAsyncTaskGovernanceProvider(
            20,
            "ops",
            "Ops Recovery",
            () -> opsRecoveryController.summarizeHistoryExportAsyncTaskSnapshot(null, null)
        );
    }

    @Bean
    AsyncTaskGovernanceProvider searchAsyncTaskGovernanceProvider(
        SearchController searchController
    ) {
        return new SimpleAsyncTaskGovernanceProvider(
            30,
            "search",
            "Search",
            () -> searchController.summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTaskSnapshot(null)
        );
    }

    @Bean
    AsyncTaskGovernanceProvider previewAsyncTaskGovernanceProvider(
        PreviewDiagnosticsController previewDiagnosticsController
    ) {
        return new SimpleAsyncTaskGovernanceProvider(
            40,
            "preview",
            "Preview",
            () -> previewDiagnosticsController.summarizeRenditionResourcesCsvAsyncExportTaskSnapshot(null)
        );
    }

    @Bean
    AsyncTaskGovernanceProvider batchDownloadAsyncTaskGovernanceProvider(
        BatchDownloadAsyncTaskRegistry batchDownloadAsyncTaskRegistry
    ) {
        return new SimpleAsyncTaskGovernanceProvider(
            50,
            "batchDownload",
            "Batch Download",
            () -> AsyncTaskSummaryAdapters.fromBatchDownload(batchDownloadAsyncTaskRegistry.summary())
        );
    }
}
