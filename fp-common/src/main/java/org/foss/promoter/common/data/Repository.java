package org.foss.promoter.common.data;

public class Repository {
    private String name;
    private String transactionId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public String toString() {
        return "Repository{" +
                "name='" + name + '\'' +
                ", transactionId='" + transactionId + '\'' +
                '}';
    }
}
