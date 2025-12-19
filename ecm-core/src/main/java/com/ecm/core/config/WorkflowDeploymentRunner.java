package com.ecm.core.config;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.DeploymentBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnClass(RepositoryService.class)
@ConditionalOnProperty(name = "ecm.workflow.auto-deploy", havingValue = "true", matchIfMissing = true)
public class WorkflowDeploymentRunner implements ApplicationRunner {

    private static final String DEFAULT_DEPLOYMENT_NAME = "ecm-workflows";

    private final RepositoryService repositoryService;
    private final ResourcePatternResolver resolver;

    @Value("${ecm.workflow.definitions-path:classpath:workflows/}")
    private String definitionsPath;

    public WorkflowDeploymentRunner(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
        this.resolver = new PathMatchingResourcePatternResolver();
    }

    @Override
    public void run(ApplicationArguments args) {
        String basePath = normalizeBasePath(definitionsPath);

        Map<String, Resource> resources = new LinkedHashMap<>();
        collectResources(resources, "classpath*:" + basePath + "**/*.bpmn20.xml");
        collectResources(resources, "classpath*:" + basePath + "**/*.bpmn");

        if (resources.isEmpty()) {
            log.warn("No workflow definitions found under {}", basePath);
            return;
        }

        DeploymentBuilder builder = repositoryService.createDeployment()
            .name(DEFAULT_DEPLOYMENT_NAME)
            .key(DEFAULT_DEPLOYMENT_NAME)
            .enableDuplicateFiltering();

        int added = 0;
        for (Map.Entry<String, Resource> entry : resources.entrySet()) {
            try {
                builder.addBytes(entry.getKey(), entry.getValue().getContentAsByteArray());
                added += 1;
            } catch (IOException ex) {
                log.warn("Failed to read workflow resource: {}", entry.getKey(), ex);
            }
        }

        if (added == 0) {
            log.warn("No readable workflow definitions found under {}", basePath);
            return;
        }

        builder.deploy();
        log.info("Deployed {} workflow definition(s) from {}", added, basePath);
    }

    private void collectResources(Map<String, Resource> resources, String pattern) {
        try {
            for (Resource resource : resolver.getResources(pattern)) {
                String name = toResourceName(resource);
                resources.putIfAbsent(name, resource);
            }
        } catch (IOException ex) {
            log.warn("Failed to resolve workflow resources for pattern {}", pattern, ex);
        }
    }

    private String normalizeBasePath(String path) {
        String base = path == null ? "" : path.trim();
        base = base.replaceFirst("^classpath\\*?:", "");
        if (base.startsWith("/")) {
            base = base.substring(1);
        }
        if (!base.isEmpty() && !base.endsWith("/")) {
            base = base + "/";
        }
        return base;
    }

    private String toResourceName(Resource resource) {
        String description = resource.getDescription();
        int start = description.indexOf('[');
        int end = description.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return description.substring(start + 1, end);
        }
        String filename = resource.getFilename();
        return filename != null ? filename : description;
    }
}
