package com.shuatibao.model;

import java.util.List;

public class ExamPaper {
    private String id;
    private String title;
    private int questionCount;
    private List<Question> questions;
    private long createTime;
    private String sourceFile;

    public ExamPaper() {}

    public ExamPaper(String title, List<Question> questions, String sourceFile) {
        this.title = title;
        this.questions = questions;
        this.questionCount = questions != null ? questions.size() : 0;
        this.sourceFile = sourceFile;
        this.createTime = System.currentTimeMillis();
        this.id = String.valueOf(createTime);
    }

    // getter 和 setter 方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getQuestionCount() {
        if (questions != null) {
            return questions.size();
        }
        return questionCount;
    }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) {
        this.questions = questions;
        this.questionCount = questions != null ? questions.size() : 0;
    }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
}