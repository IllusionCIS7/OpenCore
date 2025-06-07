package com.illusioncis7.opencore.config;

public class ConfigParameter {
    private int id;
    private String path;
    private String parameterPath;
    private int minValue;
    private int maxValue;
    private String description;
    private String recommendedRange;
    private boolean editable;
    private String impactCategory;
    private int impactRating = 5;
    private String currentValue;

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

    public int getMinValue() {
        return minValue;
    }

    public void setMinValue(int minValue) {
        this.minValue = minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }

    /**
     * Validate the provided value against this parameter's constraints.
     *
     * @param value value to check
     * @return {@code true} if valid
     */
    public boolean isValid(Object value) {
        if (value == null) {
            return false;
        }
        try {
            int v;
            if (value instanceof Number) {
                v = ((Number) value).intValue();
            } else {
                v = Integer.parseInt(value.toString());
            }
            return v >= minValue && v <= maxValue;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Return validation warnings for a value if it violates constraints.
     */
    public String getValidationWarnings(Object value) {
        StringBuilder sb = new StringBuilder();
        if (!editable) {
            sb.append("Parameter not editable");
        }
        if (!isValid(value)) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("Value outside allowed range [").append(minValue).append("-").append(maxValue).append("]");
        }
        return sb.toString();
    }
}
