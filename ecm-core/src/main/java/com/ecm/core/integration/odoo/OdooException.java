package com.ecm.core.integration.odoo;

public class OdooException extends Exception {
    
    public OdooException(String message) {
        super(message);
    }
    
    public OdooException(String message, Throwable cause) {
        super(message, cause);
    }
}