package com.grpc.grpc.safety.template;

import java.util.ArrayList;
import java.util.List;

public final class SafetyStatementTemplate {
    private SafetyStatementTemplate() {}

    public static List<Section> sections() {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Health and Safety Statement",
                "All pest control works will be carried out safely, professionally, and in accordance with current health and safety legislation, product label instructions, manufacturer guidance, and recognised industry best practice."));
        sections.add(new Section("Risk Assessment",
                "Before work begins, a site-specific risk assessment will be completed to identify hazards, access issues, risks to occupants, staff, pets, wildlife, the public, and the surrounding environment."));
        sections.add(new Section("Competent Personnel",
                "Only trained and authorised technicians will carry out pest control treatments, proofing works, inspections, and installations. Appropriate PPE will be worn where required."));
        sections.add(new Section("Safe Use of Products",
                "All pesticides, biocides, traps, monitoring devices, proofing materials, and application methods will be used in accordance with product labels and relevant regulations. The minimum effective amount of chemical product will be used where treatment is required."));
        sections.add(new Section("Client Safety Advice",
                "Clients will be advised of any required preparation, re-entry times, ventilation requirements, restrictions, or post-treatment precautions."));
        sections.add(new Section("Working at Height and Tools",
                "Where ladders, roof access, drilling, or installation works are required, safe working practices will be followed. Work areas will be kept tidy and secure to reduce trip hazards and prevent unauthorised access."));
        sections.add(new Section("Wildlife and Environmental Protection",
                "Protected species, nesting birds, and non-target animals will not be disturbed. If suspected protected wildlife activity is found, works will be stopped or adjusted as required."));
        sections.add(new Section("Waste Disposal",
                "Waste materials, contaminated items, and pest-related debris will be removed and disposed of responsibly in line with environmental and waste requirements."));
        return sections;
    }

    public static final class Section {
        public final String title;
        public final String body;

        public Section(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }
}
