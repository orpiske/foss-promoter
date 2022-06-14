package org.foss.promoter.common.data;

public class Repository {
    private String name;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Repository{" +
                "name='" + name + '\'' +
                '}';
    }
}
