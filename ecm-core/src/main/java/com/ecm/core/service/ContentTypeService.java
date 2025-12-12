package com.ecm.core.service;

import com.ecm.core.entity.ContentType;
import com.ecm.core.entity.ContentType.PropertyDefinition;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.ContentTypeRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentTypeService {

    private final ContentTypeRepository contentTypeRepository;
    private final NodeRepository nodeRepository;

    public ContentType createType(ContentType contentType) {
        if (contentTypeRepository.findByName(contentType.getName()).isPresent()) {
            throw new IllegalArgumentException("Type already exists: " + contentType.getName());
        }
        return contentTypeRepository.save(contentType);
    }

    public List<ContentType> getAllTypes() {
        return contentTypeRepository.findAll();
    }

    public ContentType getType(String name) {
        return contentTypeRepository.findByName(name)
            .orElseThrow(() -> new NoSuchElementException("Type not found: " + name));
    }

    /**
     * Validate and Apply metadata to a node based on a Content Type
     */
    @Transactional
    public Node applyType(UUID nodeId, String typeName, Map<String, Object> properties) {
        Node node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
        
        ContentType type = getType(typeName);
        
        // Validate properties against schema
        List<String> errors = validateProperties(type, properties);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }

        // Apply
        if (node.getProperties() == null) {
            node.setProperties(new HashMap<>());
        }
        node.getProperties().putAll(properties);
        
        // Store the type name in metadata for reference
        if (node.getMetadata() == null) {
            node.setMetadata(new HashMap<>());
        }
        node.getMetadata().put("ecm:contentType", typeName);

        return nodeRepository.save(node);
    }

    private List<String> validateProperties(ContentType type, Map<String, Object> properties) {
        List<String> errors = new ArrayList<>();

        for (PropertyDefinition prop : type.getProperties()) {
            Object value = properties.get(prop.getName());

            // Check required
            if (prop.isRequired() && (value == null || value.toString().isBlank())) {
                errors.add("Missing required property: " + prop.getTitle());
                continue;
            }

            if (value != null) {
                // Check regex
                if (prop.getRegex() != null && !prop.getRegex().isEmpty()) {
                    if (!Pattern.matches(prop.getRegex(), value.toString())) {
                        errors.add("Property " + prop.getTitle() + " format invalid");
                    }
                }

                // Check type (Basic implementation)
                try {
                    switch (prop.getType().toLowerCase()) {
                        case "number":
                            Double.parseDouble(value.toString());
                            break;
                        case "date":
                            // Simple ISO date check
                            LocalDate.parse(value.toString(), DateTimeFormatter.ISO_DATE);
                            break;
                        case "boolean":
                            if (!value.toString().equalsIgnoreCase("true") && !value.toString().equalsIgnoreCase("false")) {
                                throw new Exception();
                            }
                            break;
                        // text is always valid
                    }
                } catch (Exception e) {
                    errors.add("Property " + prop.getTitle() + " must be of type " + prop.getType());
                }
                
                // Check options
                if (prop.getOptions() != null && !prop.getOptions().isEmpty()) {
                    if (!prop.getOptions().contains(value.toString())) {
                         errors.add("Property " + prop.getTitle() + " must be one of: " + prop.getOptions());
                    }
                }
            }
        }
        return errors;
    }
}
