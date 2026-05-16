package com.notify.agent.client.models;

public class AttributeModel {
    private String name; // e.g. "orderId"
    private String type; // e.g. "String"
    private String description; // e.g. "Order ID"
    private String defaultValue; // optional

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
