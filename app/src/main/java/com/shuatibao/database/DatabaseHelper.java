package com.shuatibao.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.shuatibao.model.ExamPaper;
import com.shuatibao.model.Question;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "question_bank.db";
    private static final int DATABASE_VERSION = 1;

    // 试卷表
    private static final String TABLE_EXAM_PAPER = "exam_paper";
    private static final String COLUMN_PAPER_ID = "paper_id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_SOURCE_FILE = "source_file";
    private static final String COLUMN_CREATE_TIME = "create_time";

    // 题目表
    private static final String TABLE_QUESTION = "question";
    private static final String COLUMN_QUESTION_ID = "question_id";
    private static final String COLUMN_PAPER_ID_FK = "paper_id_fk";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_OPTIONS = "options";
    private static final String COLUMN_ANSWERS = "answers";
    private static final String COLUMN_ANALYSIS = "analysis";

    // 创建错题表
    private static final String TABLE_WRONG_QUESTIONS = "wrong_questions";
    private static final String COLUMN_WRONG_ID = "wrong_id";
    private static final String COLUMN_WRONG_TIME = "wrong_time";
    private static final String COLUMN_WRONG_COUNT = "wrong_count";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建试卷表
        String createExamPaperTable = "CREATE TABLE " + TABLE_EXAM_PAPER + "(" +
                COLUMN_PAPER_ID + " TEXT PRIMARY KEY," +
                COLUMN_TITLE + " TEXT," +
                COLUMN_SOURCE_FILE + " TEXT," +
                COLUMN_CREATE_TIME + " INTEGER" +
                ")";
        db.execSQL(createExamPaperTable);

        // 创建题目表
        String createQuestionTable = "CREATE TABLE " + TABLE_QUESTION + "(" +
                COLUMN_QUESTION_ID + " TEXT PRIMARY KEY," +
                COLUMN_PAPER_ID_FK + " TEXT," +
                COLUMN_TYPE + " TEXT," +
                COLUMN_CONTENT + " TEXT," +
                COLUMN_OPTIONS + " TEXT," +
                COLUMN_ANSWERS + " TEXT," +
                COLUMN_ANALYSIS + " TEXT," +
                "FOREIGN KEY(" + COLUMN_PAPER_ID_FK + ") REFERENCES " +
                TABLE_EXAM_PAPER + "(" + COLUMN_PAPER_ID + ")" +
                ")";
        db.execSQL(createQuestionTable);

        // 创建错题表
        String createWrongQuestionsTable = "CREATE TABLE " + TABLE_WRONG_QUESTIONS + "(" +
                COLUMN_WRONG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_QUESTION_ID + " TEXT," +
                COLUMN_PAPER_ID_FK + " TEXT," +
                COLUMN_TYPE + " TEXT," +
                COLUMN_CONTENT + " TEXT," +
                COLUMN_OPTIONS + " TEXT," +
                COLUMN_ANSWERS + " TEXT," +
                COLUMN_ANALYSIS + " TEXT," +
                COLUMN_WRONG_TIME + " INTEGER," +
                COLUMN_WRONG_COUNT + " INTEGER DEFAULT 1," +
                "FOREIGN KEY(" + COLUMN_PAPER_ID_FK + ") REFERENCES " +
                TABLE_EXAM_PAPER + "(" + COLUMN_PAPER_ID + ")" +
                ")";
        db.execSQL(createWrongQuestionsTable);

    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WRONG_QUESTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTION);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EXAM_PAPER);
        onCreate(db);
    }

    // 在 DatabaseHelper.java 中添加以下完整的方法

    // 获取所有错题（按错误时间降序排列）
    public List<Question> getAllWrongQuestions() {
        List<Question> wrongQuestions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_WRONG_QUESTIONS +
                " ORDER BY " + COLUMN_WRONG_TIME + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                Question question = extractQuestionFromCursor(cursor);
                wrongQuestions.add(question);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return wrongQuestions;
    }

    // 获取所有错题（按错误次数降序排列）
    public List<Question> getAllWrongQuestionsByWrongCount() {
        List<Question> wrongQuestions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_WRONG_QUESTIONS +
                " ORDER BY " + COLUMN_WRONG_COUNT + " DESC, " + COLUMN_WRONG_TIME + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                Question question = extractQuestionFromCursor(cursor);
                wrongQuestions.add(question);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return wrongQuestions;
    }

    // 按关键字搜索错题
    public List<Question> searchWrongQuestions(String keyword) {
        List<Question> wrongQuestions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_WRONG_QUESTIONS +
                " WHERE " + COLUMN_CONTENT + " LIKE ? OR " + COLUMN_ANALYSIS + " LIKE ?" +
                " ORDER BY " + COLUMN_WRONG_COUNT + " DESC, " + COLUMN_WRONG_TIME + " DESC";

        String searchKeyword = "%" + keyword + "%";
        Cursor cursor = db.rawQuery(query, new String[]{searchKeyword, searchKeyword});

        if (cursor.moveToFirst()) {
            do {
                Question question = extractQuestionFromCursor(cursor);
                wrongQuestions.add(question);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return wrongQuestions;
    }

    // 从Cursor中提取Question对象的辅助方法
    private Question extractQuestionFromCursor(Cursor cursor) {
        Question question = new Question();

        int questionIdIndex = cursor.getColumnIndex(COLUMN_QUESTION_ID);
        int typeIndex = cursor.getColumnIndex(COLUMN_TYPE);
        int contentIndex = cursor.getColumnIndex(COLUMN_CONTENT);
        int optionsIndex = cursor.getColumnIndex(COLUMN_OPTIONS);
        int answersIndex = cursor.getColumnIndex(COLUMN_ANSWERS);
        int analysisIndex = cursor.getColumnIndex(COLUMN_ANALYSIS);
        int wrongTimeIndex = cursor.getColumnIndex(COLUMN_WRONG_TIME);
        int wrongCountIndex = cursor.getColumnIndex(COLUMN_WRONG_COUNT);

        if (questionIdIndex != -1) question.setId(cursor.getString(questionIdIndex));
        if (typeIndex != -1) question.setType(cursor.getString(typeIndex));
        if (contentIndex != -1) question.setContent(cursor.getString(contentIndex));
        if (optionsIndex != -1) question.setOptions(stringToList(cursor.getString(optionsIndex)));
        if (answersIndex != -1) question.setAnswers(stringToList(cursor.getString(answersIndex)));
        if (analysisIndex != -1) question.setAnalysis(cursor.getString(analysisIndex));

        // 设置错题相关属性
        if (wrongTimeIndex != -1) question.setWrongTime(cursor.getLong(wrongTimeIndex));
        if (wrongCountIndex != -1) question.setWrongCount(cursor.getInt(wrongCountIndex));

        return question;
    }

    // 获取错题统计信息
    public Map<String, Integer> getWrongQuestionStats() {
        Map<String, Integer> stats = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // 获取总错题数
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_WRONG_QUESTIONS, null);
        if (cursor.moveToFirst()) {
            stats.put("total", cursor.getInt(0));
        }
        cursor.close();

        // 获取总错误次数
        cursor = db.rawQuery("SELECT SUM(" + COLUMN_WRONG_COUNT + ") FROM " + TABLE_WRONG_QUESTIONS, null);
        if (cursor.moveToFirst()) {
            int totalWrongCount = cursor.getInt(0);
            stats.put("totalWrongCount", totalWrongCount);
        } else {
            stats.put("totalWrongCount", 0);
        }
        cursor.close();

        db.close();
        return stats;
    }




    // 在 DatabaseHelper.java 中修复 addWrongQuestion 方法
    public long addWrongQuestion(Question question, String paperId) {
        SQLiteDatabase db = this.getWritableDatabase();
        long result = -1;

        try {
            // 检查是否已经存在相同的错题
            String checkQuery = "SELECT * FROM " + TABLE_WRONG_QUESTIONS +
                    " WHERE " + COLUMN_QUESTION_ID + " = ?";
            Cursor cursor = db.rawQuery(checkQuery, new String[]{question.getId()});

            if (cursor != null && cursor.moveToFirst()) {
                // 如果已存在，更新错误次数和时间
                int wrongCountIndex = cursor.getColumnIndex(COLUMN_WRONG_COUNT);
                int wrongCount = 1;
                if (wrongCountIndex != -1) {
                    wrongCount = cursor.getInt(wrongCountIndex) + 1;
                }
                cursor.close();

                ContentValues values = new ContentValues();
                values.put(COLUMN_WRONG_TIME, System.currentTimeMillis());
                values.put(COLUMN_WRONG_COUNT, wrongCount);

                result = db.update(TABLE_WRONG_QUESTIONS, values,
                        COLUMN_QUESTION_ID + " = ?", new String[]{question.getId()});
            } else {
                if (cursor != null) {
                    cursor.close();
                }

                // 如果不存在，插入新记录
                ContentValues values = new ContentValues();
                values.put(COLUMN_QUESTION_ID, question.getId() != null ? question.getId() : "");
                values.put(COLUMN_PAPER_ID_FK, paperId != null ? paperId : "");
                values.put(COLUMN_TYPE, question.getType() != null ? question.getType() : "single_choice");
                values.put(COLUMN_CONTENT, question.getContent() != null ? question.getContent() : "");
                values.put(COLUMN_OPTIONS, listToString(question.getOptions()));
                values.put(COLUMN_ANSWERS, listToString(question.getAnswers()));
                values.put(COLUMN_ANALYSIS, question.getAnalysis() != null ? question.getAnalysis() : "");
                values.put(COLUMN_WRONG_TIME, System.currentTimeMillis());
                values.put(COLUMN_WRONG_COUNT, 1);

                result = db.insert(TABLE_WRONG_QUESTIONS, null, values);
            }
        } catch (Exception e) {
            Log.e("DatabaseHelper", "保存错题时发生异常: " + e.getMessage(), e);
        } finally {
            db.close();
        }

        return result;
    }

    // 从错题本中移除题目
    public int removeWrongQuestion(String questionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_WRONG_QUESTIONS,
                COLUMN_QUESTION_ID + " = ?",
                new String[]{questionId});
        db.close();
        return result;
    }

    // 清空错题本
    public void clearWrongQuestions() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_WRONG_QUESTIONS, null, null);
        db.close();
    }

    // 获取错题数量
    public int getWrongQuestionCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_WRONG_QUESTIONS, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }

    // 保存试卷
    public long saveExamPaper(ExamPaper examPaper) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_PAPER_ID, examPaper.getId());
        values.put(COLUMN_TITLE, examPaper.getTitle());
        values.put(COLUMN_SOURCE_FILE, examPaper.getSourceFile());
        values.put(COLUMN_CREATE_TIME, examPaper.getCreateTime());

        long result = db.insert(TABLE_EXAM_PAPER, null, values);

        // 保存题目
        if (result != -1 && examPaper.getQuestions() != null) {
            for (Question question : examPaper.getQuestions()) {
                saveQuestion(question, examPaper.getId());
            }
        }

        db.close();
        return result;
    }

    // 保存题目
    private long saveQuestion(Question question, String paperId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_QUESTION_ID, question.getId());
        values.put(COLUMN_PAPER_ID_FK, paperId);
        values.put(COLUMN_TYPE, question.getType());
        values.put(COLUMN_CONTENT, question.getContent());
        values.put(COLUMN_OPTIONS, listToString(question.getOptions()));
        values.put(COLUMN_ANSWERS, listToString(question.getAnswers()));
        values.put(COLUMN_ANALYSIS, question.getAnalysis());

        long result = db.insert(TABLE_QUESTION, null, values);
        db.close();
        return result;
    }

    // 获取所有试卷
    public List<ExamPaper> getAllExamPapers() {
        List<ExamPaper> examPapers = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_EXAM_PAPER + " ORDER BY " + COLUMN_CREATE_TIME + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                ExamPaper paper = new ExamPaper();
                paper.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PAPER_ID)));
                paper.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)));
                paper.setSourceFile(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_FILE)));
                paper.setCreateTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATE_TIME)));

                // 获取该试卷的题目
                List<Question> questions = getQuestionsByPaperId(paper.getId());
                paper.setQuestions(questions);

                examPapers.add(paper);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return examPapers;
    }

    // 根据试卷ID获取题目
    private List<Question> getQuestionsByPaperId(String paperId) {
        List<Question> questions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_QUESTION + " WHERE " + COLUMN_PAPER_ID_FK + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{paperId});

        if (cursor.moveToFirst()) {
            do {
                Question question = new Question();
                question.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_QUESTION_ID)));
                question.setType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)));
                question.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)));
                question.setOptions(stringToList(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OPTIONS))));
                question.setAnswers(stringToList(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ANSWERS))));
                question.setAnalysis(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ANALYSIS)));

                questions.add(question);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return questions;
    }

    // 删除试卷
    public void deleteExamPaper(String paperId) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            // 先删除关联的错题记录
            db.delete(TABLE_WRONG_QUESTIONS, COLUMN_PAPER_ID_FK + " = ?", new String[]{paperId});

            // 再删除关联的题目
            db.delete(TABLE_QUESTION, COLUMN_PAPER_ID_FK + " = ?", new String[]{paperId});

            // 最后删除试卷
            db.delete(TABLE_EXAM_PAPER, COLUMN_PAPER_ID + " = ?", new String[]{paperId});

        } catch (Exception e) {
            Log.e("DatabaseHelper", "删除试卷失败: " + e.getMessage(), e);
        } finally {
            db.close();
        }
    }
    // 在 DatabaseHelper 类中添加以下方法
    public void initializeSampleData() {
        if (isDatabaseEmpty()) {
            createSampleExamPapers();
        }
    }

    private boolean isDatabaseEmpty() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_EXAM_PAPER, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count == 0;
    }

    private void createSampleExamPapers() {
        // 创建示例试卷1：数学基础测试
        ExamPaper mathPaper = createMathSamplePaper();
        saveExamPaper(mathPaper);

        // 创建示例试卷2：编程基础测试
        ExamPaper programmingPaper = createProgrammingSamplePaper();
        saveExamPaper(programmingPaper);

        // 创建示例试卷3：英语语法测试
        ExamPaper englishPaper = createEnglishSamplePaper();
        saveExamPaper(englishPaper);
    }

    // 在 DatabaseHelper 类中修正示例数据创建方法
    private ExamPaper createMathSamplePaper() {
        List<Question> questions = new ArrayList<>();

        // 第1题 - 单选题
        List<String> options1 = new ArrayList<>();
        options1.add("A. 1");
        options1.add("B. 2");
        options1.add("C. 3");
        options1.add("D. 4");
        List<String> answers1 = new ArrayList<>();
        answers1.add("B");
        Question question1 = new Question("single_choice", "1 + 1 = ?", options1, answers1, "基础加法运算");
        question1.setId(System.currentTimeMillis() + "_math_1"); // 使用时间戳确保唯一性
        questions.add(question1);

        // 第2题 - 多选题
        List<String> options2 = new ArrayList<>();
        options2.add("A. 2");
        options2.add("B. 4");
        options2.add("C. 6");
        options2.add("D. 8");
        List<String> answers2 = new ArrayList<>();
        answers2.add("B");
        answers2.add("D");
        Question question2 = new Question("multiple_choice", "以下哪些数是4的倍数？", options2, answers2, "4的倍数有4, 8, 12等");
        question2.setId(System.currentTimeMillis() + "_math_2");
        questions.add(question2);

        // 第3题 - 判断题
        List<String> answers3 = new ArrayList<>();
        answers3.add("正确");
        Question question3 = new Question("judgment", "三角形的内角和是180度", new ArrayList<>(), answers3, "这是平面几何的基本定理");
        question3.setId(System.currentTimeMillis() + "_math_3");
        questions.add(question3);

        // 第4题 - 填空题
        List<String> answers4 = new ArrayList<>();
        answers4.add("勾股定理");
        Question question4 = new Question("fill_blank", "直角三角形中，两直角边的平方和等于斜边的平方，这个定理叫做______", new ArrayList<>(), answers4, "勾股定理是几何学中的重要定理");
        question4.setId(System.currentTimeMillis() + "_math_4");
        questions.add(question4);

        // 第5题 - 复杂单选题
        List<String> options5 = new ArrayList<>();
        options5.add("A. 12");
        options5.add("B. 14");
        options5.add("C. 16");
        options5.add("D. 20");
        List<String> answers5 = new ArrayList<>();
        answers5.add("D");
        Question question5 = new Question("single_choice", "一个长方形的长是6cm，宽是4cm，它的周长是多少？", options5, answers5, "周长 = 2 × (长 + 宽) = 2 × (6 + 4) = 20cm");
        question5.setId(System.currentTimeMillis() + "_math_5");
        questions.add(question5);

        ExamPaper paper = new ExamPaper("数学基础测试", questions, "系统示例");
        paper.setId("math_sample_paper_" + System.currentTimeMillis());
        return paper;
    }

    private ExamPaper createProgrammingSamplePaper() {
        List<Question> questions = new ArrayList<>();

        // 第1题 - 多选题
        List<String> options1 = new ArrayList<>();
        options1.add("A. Java");
        options1.add("B. Python");
        options1.add("C. HTML");
        options1.add("D. CSS");
        List<String> answers1 = new ArrayList<>();
        answers1.add("A");
        answers1.add("B");
        Question question1 = new Question("multiple_choice", "以下哪些是编程语言？", options1, answers1, "HTML和CSS是标记语言，不是编程语言");
        question1.setId(System.currentTimeMillis() + "_prog_1");
        questions.add(question1);

        // 第2题 - 判断题
        List<String> answers2 = new ArrayList<>();
        answers2.add("正确");
        Question question2 = new Question("judgment", "Java是一种面向对象的编程语言", new ArrayList<>(), answers2, "Java的设计理念就是面向对象编程");
        question2.setId(System.currentTimeMillis() + "_prog_2");
        questions.add(question2);

        // 第3题 - 填空题
        List<String> answers3 = new ArrayList<>();
        answers3.add("面向对象");
        Question question3 = new Question("fill_blank", "Java是一种______的编程语言", new ArrayList<>(), answers3, "Java的核心特性就是面向对象");
        question3.setId(System.currentTimeMillis() + "_prog_3");
        questions.add(question3);

        // 第4题 - 单选题
        List<String> options4 = new ArrayList<>();
        options4.add("A. 编译型语言");
        options4.add("B. 解释型语言");
        options4.add("C. 标记语言");
        options4.add("D. 脚本语言");
        List<String> answers4 = new ArrayList<>();
        answers4.add("A");
        Question question4 = new Question("single_choice", "Java属于哪种类型的语言？", options4, answers4, "Java需要先编译成字节码，然后在JVM上运行");
        question4.setId(System.currentTimeMillis() + "_prog_4");
        questions.add(question4);

        // 第5题 - 多选题
        List<String> options5 = new ArrayList<>();
        options5.add("A. 封装");
        options5.add("B. 继承");
        options5.add("C. 多态");
        options5.add("D. 循环");
        List<String> answers5 = new ArrayList<>();
        answers5.add("A");
        answers5.add("B");
        answers5.add("C");
        Question question5 = new Question("multiple_choice", "面向对象编程的三大特性包括哪些？", options5, answers5, "封装、继承、多态是面向对象编程的三大核心特性");
        question5.setId(System.currentTimeMillis() + "_prog_5");
        questions.add(question5);

        ExamPaper paper = new ExamPaper("编程基础测试", questions, "系统示例");
        paper.setId("programming_sample_paper_" + System.currentTimeMillis());
        return paper;
    }

    private ExamPaper createEnglishSamplePaper() {
        List<Question> questions = new ArrayList<>();

        // 第1题 - 单选题
        List<String> options1 = new ArrayList<>();
        options1.add("A. am");
        options1.add("B. is");
        options1.add("C. are");
        options1.add("D. be");
        List<String> answers1 = new ArrayList<>();
        answers1.add("B");
        Question question1 = new Question("single_choice", "He ______ a student.", options1, answers1, "第三人称单数用is");
        question1.setId(System.currentTimeMillis() + "_eng_1");
        questions.add(question1);

        // 第2题 - 填空题
        List<String> answers2 = new ArrayList<>();
        answers2.add("beautiful");
        Question question2 = new Question("fill_blank", "The flowers in the garden are ______.", new ArrayList<>(), answers2, "beautiful是形容词，修饰flowers");
        question2.setId(System.currentTimeMillis() + "_eng_2");
        questions.add(question2);

        // 第3题 - 判断题
        List<String> answers3 = new ArrayList<>();
        answers3.add("错误");
        Question question3 = new Question("judgment", "\"I has a book\" 这个句子语法正确", new ArrayList<>(), answers3, "第一人称应该用have，不是has");
        question3.setId(System.currentTimeMillis() + "_eng_3");
        questions.add(question3);

        // 第4题 - 单选题
        List<String> options4 = new ArrayList<>();
        options4.add("A. on");
        options4.add("B. in");
        options4.add("C. at");
        options4.add("D. to");
        List<String> answers4 = new ArrayList<>();
        answers4.add("B");
        Question question4 = new Question("single_choice", "I live ______ Beijing.", options4, answers4, "大城市前用in");
        question4.setId(System.currentTimeMillis() + "_eng_4");
        questions.add(question4);

        // 第5题 - 多选题
        List<String> options5 = new ArrayList<>();
        options5.add("A. apple");
        options5.add("B. banana");
        options5.add("C. water");
        options5.add("D. book");
        List<String> answers5 = new ArrayList<>();
        answers5.add("A");
        answers5.add("B");
        Question question5 = new Question("multiple_choice", "以下哪些是可数名词？", options5, answers5, "apple和banana是可数名词，water是不可数名词，book虽然是可数名词但不在正确选项中");
        question5.setId(System.currentTimeMillis() + "_eng_5");
        questions.add(question5);

        ExamPaper paper = new ExamPaper("英语语法测试", questions, "系统示例");
        paper.setId("english_sample_paper_" + System.currentTimeMillis());
        return paper;
    }
    // 工具方法：List转String（用逗号分隔）
    private String listToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            sb.append(item).append(",");
        }
        return sb.substring(0, sb.length() - 1);
    }

    // 工具方法：String转List
    private List<String> stringToList(String str) {
        List<String> list = new ArrayList<>();
        if (str != null && !str.isEmpty()) {
            String[] items = str.split(",");
            for (String item : items) {
                list.add(item.trim());
            }
        }
        return list;
    }

}