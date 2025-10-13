package com.shuatibao.model;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String type;
    private String content;
    private List<String> options;
    private List<String> answers;
    private String analysis;
    private long createTime;

    public Question() {}

    public Question(String type, String content, List<String> options,
                    List<String> answers, String analysis) {
        this.type = type;
        this.content = content;
        this.options = options;
        this.answers = answers;
        this.analysis = analysis;
        this.createTime = System.currentTimeMillis();
        this.id = String.valueOf(createTime);
    }

    // getter 和 setter 方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public List<String> getAnswers() { return answers; }
    public void setAnswers(List<String> answers) { this.answers = answers; }

    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    // 在 Question.java 中更新 getTypeName() 方法
    public String getTypeName() {
        switch (type) {
            case "single_choice": return "单选题";
            case "multiple_choice": return "多选题";
            case "judgment": return "判断题";
            case "fill_blank": return "填空题";
            case "essay": return "简答题"; // 新增简答题型
            default: return "未知题型";
        }

    }
}