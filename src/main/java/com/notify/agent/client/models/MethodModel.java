package com.notify.agent.client.models;

import java.util.List;

public class MethodModel {
    private String name; // e.g. "placeOrder"
    private String returnType; // e.g. "OrderResponse"
    private String description; // e.g. "Places an order"
    private List<ParameterModel> parameters; // method parameters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ParameterModel> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterModel> parameters) {
        this.parameters = parameters;
    }
}
