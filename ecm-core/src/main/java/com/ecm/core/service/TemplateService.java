package com.ecm.core.service;

import com.ecm.core.entity.TemplateDefinition;
import com.ecm.core.repository.TemplateDefinitionRepository;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateDefinitionRepository templateRepository;
    private final SecurityService securityService;

    private final Configuration freemarkerConfiguration = createFreemarkerConfiguration();

    @Transactional(readOnly = true)
    public List<TemplateDefinitionDto> listTemplates() {
        requireAdmin();
        return templateRepository.findAllByOrderByNameAsc().stream()
            .map(TemplateDefinitionDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public TemplateDefinitionDto getTemplate(UUID templateId) {
        requireAdmin();
        return TemplateDefinitionDto.from(getTemplateEntity(templateId));
    }

    public TemplateDefinitionDto createTemplate(TemplateMutationRequest request) {
        requireAdmin();
        String name = normalizeName(request.name());
        String templatePath = normalizePath(request.templatePath());
        if (templateRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Template name already exists: " + name);
        }
        if (templateRepository.existsByTemplatePath(templatePath)) {
            throw new IllegalArgumentException("Template path already exists: " + templatePath);
        }

        TemplateDefinition template = new TemplateDefinition();
        applyMutation(template, request, true);
        TemplateDefinition saved = templateRepository.save(template);
        log.info("Template created: {} ({})", saved.getName(), saved.getTemplatePath());
        return TemplateDefinitionDto.from(saved);
    }

    public TemplateDefinitionDto updateTemplate(UUID templateId, TemplateMutationRequest request) {
        requireAdmin();
        TemplateDefinition template = getTemplateEntity(templateId);
        String nextName = normalizeName(request.name());
        String nextPath = normalizePath(request.templatePath());
        if (!template.getName().equalsIgnoreCase(nextName) && templateRepository.existsByNameIgnoreCase(nextName)) {
            throw new IllegalArgumentException("Template name already exists: " + nextName);
        }
        if (!template.getTemplatePath().equals(nextPath) && templateRepository.existsByTemplatePath(nextPath)) {
            throw new IllegalArgumentException("Template path already exists: " + nextPath);
        }

        applyMutation(template, request, false);
        TemplateDefinition saved = templateRepository.save(template);
        log.info("Template updated: {} ({})", saved.getName(), saved.getTemplatePath());
        return TemplateDefinitionDto.from(saved);
    }

    public void deleteTemplate(UUID templateId) {
        requireAdmin();
        TemplateDefinition template = getTemplateEntity(templateId);
        templateRepository.delete(template);
        log.info("Template deleted: {} ({})", template.getName(), template.getTemplatePath());
    }

    @Transactional(readOnly = true)
    public TemplateExecutionResult executeTemplate(TemplateExecutionRequest request) {
        requireAdmin();
        Map<String, Object> model = request.model() != null ? request.model() : Map.of();
        if (request.templatePath() != null && !request.templatePath().isBlank()) {
            TemplateDefinition template = templateRepository.findByTemplatePathAndActiveTrue(request.templatePath().trim())
                .orElseThrow(() -> new NoSuchElementException("Template not found: " + request.templatePath().trim()));
            String rendered = renderTemplate(template.getTemplatePath(), template.getContent(), model);
            return new TemplateExecutionResult(rendered, template.getTemplatePath(), true, rendered.length(), LocalDateTime.now());
        }
        if (request.templateContent() != null && !request.templateContent().isBlank()) {
            String rendered = renderTemplate("inline-template", request.templateContent(), model);
            return new TemplateExecutionResult(rendered, null, false, rendered.length(), LocalDateTime.now());
        }
        throw new IllegalArgumentException("Either templatePath or templateContent is required");
    }

    private TemplateDefinition getTemplateEntity(UUID templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new NoSuchElementException("Template not found: " + templateId));
    }

    private void applyMutation(TemplateDefinition template, TemplateMutationRequest request, boolean creating) {
        template.setName(normalizeName(request.name()));
        template.setTemplatePath(normalizePath(request.templatePath()));
        template.setDescription(request.description() != null && !request.description().isBlank() ? request.description().trim() : null);
        template.setContent(normalizeContent(request.content()));
        template.setTags(normalizeTags(request.tags()));
        template.setActive(request.active() != null ? request.active() : creating || template.isActive());
        template.setEngine(TemplateDefinition.TemplateEngine.FREEMARKER);
    }

    private String renderTemplate(String name, String content, Map<String, Object> model) {
        try (StringWriter out = new StringWriter()) {
            Template template = new Template(name, new StringReader(content), freemarkerConfiguration);
            template.process(model, out);
            return out.toString();
        } catch (IOException | TemplateException ex) {
            throw new IllegalArgumentException("Template rendering failed: " + ex.getMessage(), ex);
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        return name.trim();
    }

    private String normalizePath(String templatePath) {
        if (templatePath == null || templatePath.isBlank()) {
            throw new IllegalArgumentException("Template path is required");
        }
        String normalized = templatePath.trim();
        if (!normalized.matches("[A-Za-z0-9._\\-/]+")) {
            throw new IllegalArgumentException("Template path contains unsupported characters");
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Template content is required");
        }
        return content;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .map(tag -> tag == null ? "" : tag.trim())
            .filter(tag -> !tag.isEmpty())
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin role required");
        }
    }

    private Configuration createFreemarkerConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        return configuration;
    }

    public record TemplateMutationRequest(
        String name,
        String templatePath,
        String description,
        String content,
        List<String> tags,
        Boolean active
    ) {}

    public record TemplateExecutionRequest(
        String templatePath,
        String templateContent,
        Map<String, Object> model
    ) {}

    public record TemplateExecutionResult(
        String rendered,
        String templatePath,
        boolean storedTemplate,
        int outputLength,
        LocalDateTime executedAt
    ) {}

    public record TemplateDefinitionDto(
        UUID id,
        String name,
        String templatePath,
        String description,
        String engine,
        String content,
        List<String> tags,
        boolean active,
        String createdBy,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate
    ) {
        static TemplateDefinitionDto from(TemplateDefinition template) {
            return new TemplateDefinitionDto(
                template.getId(),
                template.getName(),
                template.getTemplatePath(),
                template.getDescription(),
                template.getEngine().name(),
                template.getContent(),
                template.getTags() != null ? List.copyOf(template.getTags()) : List.of(),
                template.isActive(),
                template.getCreatedBy(),
                template.getCreatedDate(),
                template.getLastModifiedDate()
            );
        }
    }
}
