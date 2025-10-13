package com.shuatibao.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.shuatibao.R;
import com.shuatibao.model.ExamPaper;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ExamPaperAdapter extends RecyclerView.Adapter<ExamPaperAdapter.ViewHolder> {

    private List<ExamPaper> examPapers;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private Context context;

    public ExamPaperAdapter(List<ExamPaper> examPapers, Context context) {
        this.examPapers = examPapers;
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }

    public void updateData(List<ExamPaper> newExamPapers) {
        this.examPapers = newExamPapers;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < examPapers.size()) {
            examPapers.remove(position);
            notifyItemRemoved(position);
            // 通知后续item位置变化
            notifyItemRangeChanged(position, examPapers.size() - position);
        }
    }

    public ExamPaper getItem(int position) {
        if (position >= 0 && position < examPapers.size()) {
            return examPapers.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exam_paper, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExamPaper paper = examPapers.get(position);

        holder.tvPaperTitle.setText(paper.getTitle());
        holder.tvQuestionCount.setText(paper.getQuestionCount() + "题");
        holder.tvSourceFile.setText("来自：" + paper.getSourceFile());
        holder.tvCreateTime.setText(dateFormat.format(paper.getCreateTime()));

        // 如果是示例试卷，显示示例标识
        if ("系统示例".equals(paper.getSourceFile())) {
            holder.tvSampleTag.setVisibility(View.VISIBLE);
        } else {
            holder.tvSampleTag.setVisibility(View.GONE);
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(paper, position);
            }
        });

        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClickListener != null) {
                return onItemLongClickListener.onItemLongClick(paper, position);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return examPapers != null ? examPapers.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPaperTitle, tvQuestionCount, tvSourceFile, tvCreateTime, tvSampleTag;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPaperTitle = itemView.findViewById(R.id.tvPaperTitle);
            tvQuestionCount = itemView.findViewById(R.id.tvQuestionCount);
            tvSourceFile = itemView.findViewById(R.id.tvSourceFile);
            tvCreateTime = itemView.findViewById(R.id.tvCreateTime);
            tvSampleTag = itemView.findViewById(R.id.tvSampleTag);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(ExamPaper examPaper, int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(ExamPaper examPaper, int position);
    }
}