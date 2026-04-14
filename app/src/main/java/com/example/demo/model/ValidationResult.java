package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流验证结果
 */
public class ValidationResult {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;

    public ValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * 添加错误
     */
    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }

    /**
     * 添加警告
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    /**
     * 检查是否有效
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * 获取所有错误信息
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * 获取所有警告信息
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * 获取错误和警告的总数
     */
    public int getTotalIssues() {
        return errors.size() + warnings.size();
    }

    /**
     * 获取所有问题描述
     */
    public String getAllIssuesMessage() {
        StringBuilder sb = new StringBuilder();
        if (!errors.isEmpty()) {
            sb.append("错误:\n");
            for (String error : errors) {
                sb.append("- ").append(error).append("\n");
            }
        }
        if (!warnings.isEmpty()) {
            sb.append("警告:\n");
            for (String warning : warnings) {
                sb.append("- ").append(warning).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", errorCount=" + errors.size() +
                ", warningCount=" + warnings.size() +
                '}';
    }
}
