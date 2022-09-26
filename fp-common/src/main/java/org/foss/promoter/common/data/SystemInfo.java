package org.foss.promoter.common.data;

public class SystemInfo {
    private String projectVersion;
    private String camelVersion;
    private String kafkaClientVersion;

    public SystemInfo() {
    }

    public SystemInfo(String projectVersion, String camelVersion, String kafkaClientVersion) {
        this.projectVersion = projectVersion;
        this.camelVersion = camelVersion;
        this.kafkaClientVersion = kafkaClientVersion;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    public String getCamelVersion() {
        return camelVersion;
    }

    public void setCamelVersion(String camelVersion) {
        this.camelVersion = camelVersion;
    }

    public String getKafkaClientVersion() {
        return kafkaClientVersion;
    }

    public void setKafkaClientVersion(String kafkaClientVersion) {
        this.kafkaClientVersion = kafkaClientVersion;
    }
}
