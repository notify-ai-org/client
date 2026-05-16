package com.notify.agent.client.models;

import java.util.List;

public class ClassModel {
    private String packageName; // e.g. "com.notify.order"
    private String className; // e.g. "OrderService"
    private String classDescription; // e.g. "OrderService"
    private ClassType classType; // CLASS, INTERFACE, ENUM, RECORD
    private String superClass; // fully qualified name
    private List<String> interfaces; // implemented interfaces
    private List<AttributeModel> attributes; // fields
    private List<MethodModel> methods; // methods

    /**
     * @return the packageName
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * @param packageName the packageName to set
     */
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /**
     * @return the className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @param className the className to set
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * @return the classDescription
     */
    public String getClassDescription() {
        return classDescription;
    }

    /**
     * @param classDescription the classDescription to set
     */
    public void setClassDescription(String classDescription) {
        this.classDescription = classDescription;
    }

    /**
     * @return the classType
     */
    public ClassType getClassType() {
        return classType;
    }

    /**
     * @param classType the classType to set
     */
    public void setClassType(ClassType classType) {
        this.classType = classType;
    }

    /**
     * @return the superClass
     */
    public String getSuperClass() {
        return superClass;
    }

    /**
     * @param superClass the superClass to set
     */
    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    /**
     * @return the interfaces
     */
    public List<String> getInterfaces() {
        return interfaces;
    }

    /**
     * @param interfaces the interfaces to set
     */
    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }

    /**
     * @return the attributes
     */
    public List<AttributeModel> getAttributes() {
        return attributes;
    }

    /**
     * @param attributes the attributes to set
     */
    public void setAttributes(List<AttributeModel> attributes) {
        this.attributes = attributes;
    }

    /**
     * @return the methods
     */
    public List<MethodModel> getMethods() {
        return methods;
    }

    /**
     * @param methods the methods to set
     */
    public void setMethods(List<MethodModel> methods) {
        this.methods = methods;
    }

    public enum ClassType {
        CLASS,
        INTERFACE,
        ENUM,
        RECORD
    }
}
