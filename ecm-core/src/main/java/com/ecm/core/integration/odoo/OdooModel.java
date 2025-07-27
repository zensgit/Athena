package com.ecm.core.integration.odoo;

import lombok.Data;

@Data
public class OdooModel {
    private Integer id;
    private String name;
    private String model;
    private String info;
}