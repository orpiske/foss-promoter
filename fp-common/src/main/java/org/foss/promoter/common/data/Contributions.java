package org.foss.promoter.common.data;

import java.util.ArrayList;
import java.util.List;

public class Contributions {
    private List<Contribution> contributionList = new ArrayList<>();

    public List<Contribution> getContributionList() {
        return contributionList;
    }

    public void setContributionList(List<Contribution> contributionList) {
        this.contributionList = contributionList;
    }
}
