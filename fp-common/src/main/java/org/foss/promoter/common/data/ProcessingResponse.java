package org.foss.promoter.common.data;

public class ProcessingResponse {
    private String state;
    private String transactionId;


    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
