package com.grpc.grpc.email.model;

public final class EmailTemplate {
    public final String id;
    public final String name;
    public final String subject;
    public final String body;

    public EmailTemplate(String id, String name, String subject, String body) {
        this.id = id;
        this.name = name;
        this.subject = subject;
        this.body = body;
    }

    @Override
    public String toString() {
        return name;
    }
}
