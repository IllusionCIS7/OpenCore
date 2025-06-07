package com.illusioncis7.opencore.config;

public class ConfigParameter {
    private int id;
    private String path;
    private String parameterPath;
    private String minValue;
    private String maxValue;
    private String recommendedRange;
    private boolean editable;
    private String impactCategory;
    private int impactRating = 5;

    public ConfigParameter() {}

    public ConfigParameter(String path, String parameterPath) {
        this.path = path;
        this.parameterPath = parameterPath;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getParameterPath() {
        return parameterPath;
    }

    public void setParameterPath(String parameterPath) {
        this.parameterPath = parameterPath;
    }

    public String getMinValue() {
        return minValue;
    }

    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }

    public String getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    public String getRecommendedRange() {
        return recommendedRange;
    }

    public void setRecommendedRange(String recommendedRange) {
        this.recommendedRange = recommendedRange;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public String getImpactCategory() {
        return impactCategory;
    }

    public void setImpactCategory(String impactCategory) {
        this.impactCategory = impactCategory;
    }

    public int getImpactRating() {
        return impactRating;
    }

    public void setImpactRating(int impactRating) {
        this.impactRating = impactRating;
    }
}
