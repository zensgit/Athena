package com.ecm.core.integration.odoo;

import lombok.Data;
import java.util.Date;
import java.util.UUID;

@Data
public class OdooAttachment {
    private Integer id;
    private String name;
    private String model;
    private Integer resId;
    private String mimeType;
    private Long fileSize;
    private Date createDate;
    private UUID ecmDocumentId;
}