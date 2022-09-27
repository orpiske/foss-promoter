package org.foss.promoter.common.data;

public class Contribution {
    private String id;
    private CommitInfo commitInfo;
    private String encodedQrCode;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CommitInfo getCommitInfo() {
        return commitInfo;
    }

    public void setCommitInfo(CommitInfo commitInfo) {
        this.commitInfo = commitInfo;
    }

    public String getEncodedQrCode() {
        return encodedQrCode;
    }

    public void setEncodedQrCode(String encodedQrCode) {
        this.encodedQrCode = encodedQrCode;
    }
}
