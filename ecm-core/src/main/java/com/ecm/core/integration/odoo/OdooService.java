package com.ecm.core.integration.odoo;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OdooService {
    
    @Value("${ecm.odoo.url}")
    private String odooUrl;
    
    @Value("${ecm.odoo.database}")
    private String database;
    
    @Value("${ecm.odoo.username}")
    private String username;
    
    @Value("${ecm.odoo.password}")
    private String password;
    
    @Value("${ecm.odoo.api-key:}")
    private String apiKey;
    
    private final ObjectMapper objectMapper;
    
    private XmlRpcClient client;
    private Integer uid;
    
    public void initializeConnection() throws OdooException {
        try {
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(odooUrl + "/xmlrpc/2/common"));
            
            client = new XmlRpcClient();
            client.setConfig(config);
            
            // Authenticate
            Object[] params = new Object[]{database, username, password, Collections.emptyMap()};
            uid = (Integer) client.execute("authenticate", params);
            
            if (uid == null || uid <= 0) {
                throw new OdooException("Authentication failed");
            }
            
            log.info("Connected to Odoo as user ID: {}", uid);
            
        } catch (MalformedURLException | XmlRpcException e) {
            throw new OdooException("Failed to connect to Odoo", e);
        }
    }
    
    public OdooAttachment createAttachment(Document document, Integer recordId, String model) 
            throws OdooException {
        ensureConnected();
        
        try {
            Map<String, Object> attachmentData = new HashMap<>();
            attachmentData.put("name", document.getName());
            attachmentData.put("type", "binary");
            attachmentData.put("res_model", model);
            attachmentData.put("res_id", recordId);
            attachmentData.put("mimetype", document.getMimeType());
            attachmentData.put("file_size", document.getFileSize());
            attachmentData.put("description", document.getDescription());
            attachmentData.put("ecm_document_id", document.getId().toString());
            
            Object[] params = new Object[]{
                database, uid, password,
                "ir.attachment", "create",
                Arrays.asList(attachmentData)
            };
            
            Integer attachmentId = (Integer) executeCommand(params);
            
            OdooAttachment attachment = new OdooAttachment();
            attachment.setId(attachmentId);
            attachment.setName(document.getName());
            attachment.setModel(model);
            attachment.setResId(recordId);
            attachment.setEcmDocumentId(document.getId());
            
            log.info("Created Odoo attachment {} for document {}", attachmentId, document.getId());
            
            return attachment;
            
        } catch (Exception e) {
            throw new OdooException("Failed to create attachment in Odoo", e);
        }
    }
    
    public List<OdooAttachment> getAttachments(String model, Integer recordId) throws OdooException {
        ensureConnected();
        
        try {
            Object[] searchParams = new Object[]{
                database, uid, password,
                "ir.attachment", "search_read",
                Arrays.asList(Arrays.asList(
                    Arrays.asList("res_model", "=", model),
                    Arrays.asList("res_id", "=", recordId)
                )),
                new HashMap<String, Object>() {{
                    put("fields", Arrays.asList("id", "name", "mimetype", "file_size", 
                        "create_date", "ecm_document_id"));
                }}
            };
            
            Object[] results = (Object[]) executeCommand(searchParams);
            List<OdooAttachment> attachments = new ArrayList<>();
            
            for (Object result : results) {
                Map<String, Object> record = (Map<String, Object>) result;
                OdooAttachment attachment = mapToAttachment(record);
                attachments.add(attachment);
            }
            
            return attachments;
            
        } catch (Exception e) {
            throw new OdooException("Failed to get attachments from Odoo", e);
        }
    }
    
    public void linkDocumentToRecord(Document document, String model, Integer recordId, 
                                     String linkType) throws OdooException {
        ensureConnected();
        
        try {
            // Create a link record in a custom model
            Map<String, Object> linkData = new HashMap<>();
            linkData.put("document_id", document.getId().toString());
            linkData.put("document_name", document.getName());
            linkData.put("model", model);
            linkData.put("res_id", recordId);
            linkData.put("link_type", linkType);
            linkData.put("mime_type", document.getMimeType());
            linkData.put("file_size", document.getFileSize());
            
            Object[] params = new Object[]{
                database, uid, password,
                "ecm.document.link", "create",
                Arrays.asList(linkData)
            };
            
            Integer linkId = (Integer) executeCommand(params);
            log.info("Created document link {} for document {} to {}/{}", 
                linkId, document.getId(), model, recordId);
            
        } catch (Exception e) {
            throw new OdooException("Failed to link document to Odoo record", e);
        }
    }
    
    @Cacheable(value = "odooModels")
    public List<OdooModel> getModels() throws OdooException {
        ensureConnected();
        
        try {
            Object[] params = new Object[]{
                database, uid, password,
                "ir.model", "search_read",
                Arrays.asList(Arrays.asList(
                    Arrays.asList("transient", "=", false)
                )),
                new HashMap<String, Object>() {{
                    put("fields", Arrays.asList("id", "name", "model", "info"));
                    put("order", "name");
                }}
            };
            
            Object[] results = (Object[]) executeCommand(params);
            List<OdooModel> models = new ArrayList<>();
            
            for (Object result : results) {
                Map<String, Object> record = (Map<String, Object>) result;
                OdooModel model = new OdooModel();
                model.setId((Integer) record.get("id"));
                model.setName((String) record.get("name"));
                model.setModel((String) record.get("model"));
                model.setInfo((String) record.get("info"));
                models.add(model);
            }
            
            return models;
            
        } catch (Exception e) {
            throw new OdooException("Failed to get models from Odoo", e);
        }
    }
    
    public List<Map<String, Object>> searchRecords(String model, List<Object> domain, 
                                                    Map<String, Object> options) throws OdooException {
        ensureConnected();
        
        try {
            Object[] params = new Object[]{
                database, uid, password,
                model, "search_read",
                domain != null ? Arrays.asList(domain) : Arrays.asList(),
                options != null ? options : new HashMap<>()
            };
            
            Object[] results = (Object[]) executeCommand(params);
            List<Map<String, Object>> records = new ArrayList<>();
            
            for (Object result : results) {
                records.add((Map<String, Object>) result);
            }
            
            return records;
            
        } catch (Exception e) {
            throw new OdooException("Failed to search records in Odoo", e);
        }
    }
    
    public Map<String, Object> getRecord(String model, Integer recordId, List<String> fields) 
            throws OdooException {
        ensureConnected();
        
        try {
            Object[] params = new Object[]{
                database, uid, password,
                model, "read",
                Arrays.asList(recordId),
                fields != null ? fields : new ArrayList<>()
            };
            
            Object[] results = (Object[]) executeCommand(params);
            if (results != null && results.length > 0) {
                return (Map<String, Object>) results[0];
            }
            
            return null;
            
        } catch (Exception e) {
            throw new OdooException("Failed to get record from Odoo", e);
        }
    }
    
    public void syncDocumentMetadata(Document document, String model, Integer recordId) 
            throws OdooException {
        ensureConnected();
        
        try {
            // Get record data from Odoo
            Map<String, Object> record = getRecord(model, recordId, null);
            if (record == null) {
                return;
            }
            
            // Update document metadata with Odoo data
            Map<String, Object> metadata = document.getMetadata();
            metadata.put("odoo_model", model);
            metadata.put("odoo_record_id", recordId);
            metadata.put("odoo_record_name", record.get("name"));
            metadata.put("odoo_sync_date", new Date());
            
            // Add specific fields based on model
            if ("sale.order".equals(model)) {
                metadata.put("partner_name", getNestedValue(record, "partner_id", 1));
                metadata.put("amount_total", record.get("amount_total"));
                metadata.put("state", record.get("state"));
            } else if ("purchase.order".equals(model)) {
                metadata.put("partner_name", getNestedValue(record, "partner_id", 1));
                metadata.put("amount_total", record.get("amount_total"));
                metadata.put("state", record.get("state"));
            } else if ("account.invoice".equals(model)) {
                metadata.put("partner_name", getNestedValue(record, "partner_id", 1));
                metadata.put("amount_total", record.get("amount_total_signed"));
                metadata.put("state", record.get("state"));
                metadata.put("invoice_date", record.get("invoice_date"));
            }
            
            log.info("Synced metadata for document {} with Odoo {}/{}", 
                document.getId(), model, recordId);
            
        } catch (Exception e) {
            throw new OdooException("Failed to sync document metadata", e);
        }
    }
    
    public void createWorkflowTask(Node node, String workflowType, Map<String, Object> context) 
            throws OdooException {
        ensureConnected();
        
        try {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("name", "Review: " + node.getName());
            taskData.put("model", "ecm.document");
            taskData.put("res_id", node.getId().toString());
            taskData.put("activity_type_id", getActivityTypeId(workflowType));
            taskData.put("summary", context.get("summary"));
            taskData.put("date_deadline", context.get("deadline"));
            taskData.put("user_id", context.get("assignee_id"));
            
            Object[] params = new Object[]{
                database, uid, password,
                "mail.activity", "create",
                Arrays.asList(taskData)
            };
            
            Integer activityId = (Integer) executeCommand(params);
            log.info("Created Odoo activity {} for node {}", activityId, node.getId());
            
        } catch (Exception e) {
            throw new OdooException("Failed to create workflow task in Odoo", e);
        }
    }
    
    private void ensureConnected() throws OdooException {
        if (uid == null || uid <= 0) {
            initializeConnection();
        }
    }
    
    private Object executeCommand(Object[] params) throws OdooException {
        try {
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(odooUrl + "/xmlrpc/2/object"));
            
            XmlRpcClient objectClient = new XmlRpcClient();
            objectClient.setConfig(config);
            
            return objectClient.execute("execute_kw", params);
            
        } catch (Exception e) {
            throw new OdooException("Failed to execute Odoo command", e);
        }
    }
    
    private OdooAttachment mapToAttachment(Map<String, Object> record) {
        OdooAttachment attachment = new OdooAttachment();
        attachment.setId((Integer) record.get("id"));
        attachment.setName((String) record.get("name"));
        attachment.setMimeType((String) record.get("mimetype"));
        attachment.setFileSize(((Number) record.get("file_size")).longValue());
        
        String ecmDocId = (String) record.get("ecm_document_id");
        if (ecmDocId != null) {
            attachment.setEcmDocumentId(UUID.fromString(ecmDocId));
        }
        
        return attachment;
    }
    
    private Object getNestedValue(Map<String, Object> record, String field, int index) {
        Object value = record.get(field);
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            if (array.length > index) {
                return array[index];
            }
        }
        return null;
    }
    
    private Integer getActivityTypeId(String workflowType) throws OdooException {
        // This should be cached or configured
        Map<String, Integer> activityTypes = new HashMap<>();
        activityTypes.put("approval", 1);
        activityTypes.put("review", 2);
        activityTypes.put("signature", 3);
        
        return activityTypes.getOrDefault(workflowType, 1);
    }
}