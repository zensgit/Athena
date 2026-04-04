package com.ecm.core.service;

import com.ecm.core.entity.ScriptDefinition;
import com.ecm.core.repository.ScriptDefinitionRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class ScriptService {

    private static final long DEFAULT_TIMEOUT_MS = 2_000L;

    private final ScriptDefinitionRepository scriptRepository;
    private final SecurityService securityService;

    private final ExecutorService executionPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "athena-script-engine");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdownPool() {
        executionPool.shutdownNow();
    }

    @Transactional(readOnly = true)
    public List<ScriptDefinitionDto> listScripts() {
        requireAdmin();
        return scriptRepository.findAllByOrderByNameAsc().stream()
            .map(ScriptDefinitionDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ScriptDefinitionDto getScript(UUID scriptId) {
        requireAdmin();
        return ScriptDefinitionDto.from(getScriptEntity(scriptId));
    }

    public ScriptDefinitionDto createScript(ScriptMutationRequest request) {
        requireAdmin();
        String name = normalizeName(request.name());
        String scriptPath = normalizePath(request.scriptPath());
        if (scriptRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Script name already exists: " + name);
        }
        if (scriptRepository.existsByScriptPath(scriptPath)) {
            throw new IllegalArgumentException("Script path already exists: " + scriptPath);
        }

        ScriptDefinition script = new ScriptDefinition();
        applyMutation(script, request, true);
        ScriptDefinition saved = scriptRepository.save(script);
        log.info("Script created: {} ({})", saved.getName(), saved.getScriptPath());
        return ScriptDefinitionDto.from(saved);
    }

    public ScriptDefinitionDto updateScript(UUID scriptId, ScriptMutationRequest request) {
        requireAdmin();
        ScriptDefinition script = getScriptEntity(scriptId);
        String nextName = normalizeName(request.name());
        String nextPath = normalizePath(request.scriptPath());
        if (!script.getName().equalsIgnoreCase(nextName) && scriptRepository.existsByNameIgnoreCase(nextName)) {
            throw new IllegalArgumentException("Script name already exists: " + nextName);
        }
        if (!script.getScriptPath().equals(nextPath) && scriptRepository.existsByScriptPath(nextPath)) {
            throw new IllegalArgumentException("Script path already exists: " + nextPath);
        }

        applyMutation(script, request, false);
        ScriptDefinition saved = scriptRepository.save(script);
        log.info("Script updated: {} ({})", saved.getName(), saved.getScriptPath());
        return ScriptDefinitionDto.from(saved);
    }

    public void deleteScript(UUID scriptId) {
        requireAdmin();
        ScriptDefinition script = getScriptEntity(scriptId);
        scriptRepository.delete(script);
        log.info("Script deleted: {} ({})", script.getName(), script.getScriptPath());
    }

    @Transactional(readOnly = true)
    public ScriptExecutionResult executeScript(ScriptExecutionRequest request) {
        requireAdmin();
        ScriptSource source = resolveSource(request);
        Map<String, Object> model = request.model() != null ? request.model() : Map.of();
        long timeoutMs = request.timeoutMs() != null && request.timeoutMs() > 0 ? request.timeoutMs() : DEFAULT_TIMEOUT_MS;

        Future<ScriptExecutionResult> future = executionPool.submit(() -> executeInSandbox(source, model));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IllegalArgumentException("Script execution timed out after " + timeoutMs + " ms");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Script execution interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalArgumentException("Script execution failed: " + cause.getMessage(), cause);
        }
    }

    private ScriptExecutionResult executeInSandbox(ScriptSource source, Map<String, Object> model) {
        long started = System.nanoTime();
        List<String> logs = new CopyOnWriteArrayList<>();
        try (Context context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.NONE)
            .allowHostClassLookup(className -> false)
            .allowIO(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .option("engine.WarnInterpreterOnly", "false")
            .build()) {

            bindModel(context, model, logs);
            Value value = context.eval("js", source.content());
            Object result = fromValue(value);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return new ScriptExecutionResult(
                result,
                logs,
                source.scriptPath(),
                source.storedScript(),
                durationMs,
                LocalDateTime.now()
            );
        } catch (PolyglotException ex) {
            String message = ex.isCancelled()
                ? "Script execution cancelled"
                : "Script execution failed: " + ex.getMessage();
            throw new IllegalArgumentException(message, ex);
        }
    }

    private void bindModel(Context context, Map<String, Object> model, List<String> logs) {
        Value bindings = context.getBindings("js");
        bindings.putMember("model", toGuestValue(model));
        model.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                bindings.putMember(key, toGuestValue(value));
            }
        });
        bindings.putMember("logger", createLoggerProxy(logs));
        bindings.putMember("console", createLoggerProxy(logs));
        bindings.putMember("utils", ProxyObject.fromMap(Map.of(
            "now", (ProxyExecutable) arguments -> LocalDateTime.now().toString(),
            "uuid", (ProxyExecutable) arguments -> UUID.randomUUID().toString(),
            "stringify", (ProxyExecutable) arguments -> {
                if (arguments.length == 0) {
                    return "null";
                }
                Object javaValue = fromValue(arguments[0]);
                return String.valueOf(javaValue);
            }
        )));
    }

    private ProxyObject createLoggerProxy(List<String> logs) {
        return ProxyObject.fromMap(Map.of(
            "info", (ProxyExecutable) arguments -> appendLog(logs, "INFO", arguments),
            "warn", (ProxyExecutable) arguments -> appendLog(logs, "WARN", arguments),
            "error", (ProxyExecutable) arguments -> appendLog(logs, "ERROR", arguments),
            "log", (ProxyExecutable) arguments -> appendLog(logs, "LOG", arguments)
        ));
    }

    private Object appendLog(List<String> logs, String level, Value[] arguments) {
        List<String> parts = new ArrayList<>();
        for (Value argument : arguments) {
            Object javaValue = fromValue(argument);
            parts.add(String.valueOf(javaValue));
        }
        logs.add(level + ": " + String.join(" ", parts));
        return null;
    }

    private Object toGuestValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> proxyMap = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> proxyMap.put(String.valueOf(key), toGuestValue(nestedValue)));
            return ProxyObject.fromMap(proxyMap);
        }
        if (value instanceof List<?> list) {
            return ProxyArray.fromArray(list.stream().map(this::toGuestValue).toArray());
        }
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            Object[] proxy = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                proxy[i] = toGuestValue(array[i]);
            }
            return ProxyArray.fromArray(proxy);
        }
        return String.valueOf(value);
    }

    private Object fromValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.fitsInInt()) {
            return value.asInt();
        }
        if (value.fitsInLong()) {
            return value.asLong();
        }
        if (value.fitsInDouble()) {
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> items = new ArrayList<>();
            for (long index = 0; index < value.getArraySize(); index++) {
                items.add(fromValue(value.getArrayElement(index)));
            }
            return items;
        }
        if (value.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                result.put(key, fromValue(value.getMember(key)));
            }
            return result;
        }
        return value.toString();
    }

    private ScriptSource resolveSource(ScriptExecutionRequest request) {
        if (request.scriptPath() != null && !request.scriptPath().isBlank()) {
            String path = request.scriptPath().trim();
            ScriptDefinition script = scriptRepository.findByScriptPathAndActiveTrue(path)
                .orElseThrow(() -> new NoSuchElementException("Script not found: " + path));
            return new ScriptSource(script.getScriptPath(), script.getContent(), true);
        }
        if (request.scriptContent() != null && !request.scriptContent().isBlank()) {
            return new ScriptSource(null, request.scriptContent(), false);
        }
        throw new IllegalArgumentException("Either scriptPath or scriptContent is required");
    }

    private ScriptDefinition getScriptEntity(UUID scriptId) {
        return scriptRepository.findById(scriptId)
            .orElseThrow(() -> new NoSuchElementException("Script not found: " + scriptId));
    }

    private void applyMutation(ScriptDefinition script, ScriptMutationRequest request, boolean creating) {
        script.setName(normalizeName(request.name()));
        script.setScriptPath(normalizePath(request.scriptPath()));
        script.setDescription(request.description() != null && !request.description().isBlank() ? request.description().trim() : null);
        script.setContent(normalizeContent(request.content()));
        script.setTags(normalizeTags(request.tags()));
        script.setActive(request.active() != null ? request.active() : creating || script.isActive());
        script.setEngine(ScriptDefinition.ScriptEngine.GRAALJS);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Script name is required");
        }
        return name.trim();
    }

    private String normalizePath(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new IllegalArgumentException("Script path is required");
        }
        String normalized = scriptPath.trim();
        if (!normalized.matches("[A-Za-z0-9._\\-/]+")) {
            throw new IllegalArgumentException("Script path contains unsupported characters");
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Script content is required");
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

    private record ScriptSource(String scriptPath, String content, boolean storedScript) {}

    public record ScriptMutationRequest(
        String name,
        String scriptPath,
        String description,
        String content,
        List<String> tags,
        Boolean active
    ) {}

    public record ScriptExecutionRequest(
        String scriptPath,
        String scriptContent,
        Map<String, Object> model,
        Long timeoutMs
    ) {}

    public record ScriptExecutionResult(
        Object result,
        List<String> logs,
        String scriptPath,
        boolean storedScript,
        long durationMs,
        LocalDateTime executedAt
    ) {}

    public record ScriptDefinitionDto(
        UUID id,
        String name,
        String scriptPath,
        String description,
        String engine,
        String content,
        List<String> tags,
        boolean active,
        String createdBy,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate
    ) {
        static ScriptDefinitionDto from(ScriptDefinition script) {
            return new ScriptDefinitionDto(
                script.getId(),
                script.getName(),
                script.getScriptPath(),
                script.getDescription(),
                script.getEngine().name(),
                script.getContent(),
                script.getTags() != null ? List.copyOf(script.getTags()) : List.of(),
                script.isActive(),
                script.getCreatedBy(),
                script.getCreatedDate(),
                script.getLastModifiedDate()
            );
        }
    }
}
