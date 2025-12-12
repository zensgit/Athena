package com.ecm.core.integration.odoo;

import com.ecm.core.entity.Document;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.*;

/**
 * Service for integrating with Odoo ERP via XML-RPC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OdooIntegrationService {

    @Value("${ecm.odoo.url:http://odoo:8069}")
    private String odooUrl;

    @Value("${ecm.odoo.database:odoo_db}")
    private String odooDatabase;

    @Value("${ecm.odoo.username:admin}")
    private String odooUsername;

    @Value("${ecm.odoo.password:admin}")
    private String odooPassword;

    private Integer uid;

    /**
     * Authenticate with Odoo and get User ID
     */
    public boolean authenticate() {
        try {
            XmlRpcClientConfigImpl commonConfig = new XmlRpcClientConfigImpl();
            commonConfig.setServerURL(new URL(String.format("%s/xmlrpc/2/common", odooUrl)));
            
            XmlRpcClient client = new XmlRpcClient();
            client.setConfig(commonConfig);

            Object result = client.execute("authenticate", 
                Arrays.asList(odooDatabase, odooUsername, odooPassword, Collections.emptyMap()));

            if (result instanceof Integer) {
                this.uid = (Integer) result;
                log.info("Authenticated with Odoo. UID: {}", uid);
                return true;
            } else {
                log.error("Odoo authentication failed: {}", result);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to authenticate with Odoo", e);
            return false;
        }
    }

    /**
     * Create an attachment in Odoo linking to the ECM document
     */
    public Integer exportToOdoo(Document document, String model, Integer resourceId) {
        if (uid == null && !authenticate()) {
            throw new IllegalStateException("Not authenticated with Odoo");
        }

        try {
            XmlRpcClient client = getObjectClient();

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("name", document.getName());
            attachment.put("type", "url");
            attachment.put("url", "/api/v1/documents/" + document.getId() + "/download"); // ECM Download URL
            attachment.put("res_model", model);
            attachment.put("res_id", resourceId);
            attachment.put("mimetype", document.getMimeType());

            Integer attachmentId = (Integer) client.execute("execute_kw", Arrays.asList(
                odooDatabase, uid, odooPassword,
                "ir.attachment", "create",
                Collections.singletonList(attachment)
            ));

            log.info("Exported document {} to Odoo attachment {}", document.getId(), attachmentId);
            return attachmentId;

        } catch (Exception e) {
            log.error("Failed to export to Odoo", e);
            throw new RuntimeException("Odoo export failed", e);
        }
    }

    /**
     * Create a task in Odoo Project
     */
    public Integer createTask(String title, String description, Integer projectId) {
        if (uid == null && !authenticate()) {
            throw new IllegalStateException("Not authenticated with Odoo");
        }

        try {
            XmlRpcClient client = getObjectClient();

            Map<String, Object> task = new HashMap<>();
            task.put("name", title);
            task.put("description", description);
            if (projectId != null) {
                task.put("project_id", projectId);
            }

            Integer taskId = (Integer) client.execute("execute_kw", Arrays.asList(
                odooDatabase, uid, odooPassword,
                "project.task", "create",
                Collections.singletonList(task)
            ));

            log.info("Created Odoo task: {}", taskId);
            return taskId;

        } catch (Exception e) {
            log.error("Failed to create Odoo task", e);
            throw new RuntimeException("Odoo task creation failed", e);
        }
    }

    private XmlRpcClient getObjectClient() throws Exception {
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(String.format("%s/xmlrpc/2/object", odooUrl)));
        XmlRpcClient client = new XmlRpcClient();
        client.setConfig(config);
        return client;
    }
    
    @Data
    public static class OdooConfig {
        private String url;
        private String db;
        private String user;
    }
}
