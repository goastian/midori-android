package org.midorinext.android.legacy.assist;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.midorinext.android.R;

import java.util.ArrayList;

public class SuggestAdapter extends RecyclerView.Adapter<SuggestAdapter.SuggestViewHolder> {
    private final LayoutInflater inflater;
    private final Assist assist;
    private final ArrayList<String> suggestItems;

    private String searchText = "";

    private final int color_normal;
    private final int color_bold;

    public SuggestAdapter(Assist assist, ArrayList<String> suggestItems) {
        inflater = LayoutInflater.from(assist);
        this.assist = assist;
        this.suggestItems = suggestItems;
        this.color_normal = ContextCompat.getColor(assist, R.color.assist_qwant_suggest_text_light);
        this.color_bold = ContextCompat.getColor(assist, R.color.assist_qwant_suggest_text_bold);
    }

    @NonNull @Override
    public SuggestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.assist_suggestlist_item, parent, false);
        return new SuggestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SuggestViewHolder holder, int position) {
        if (position < suggestItems.size()) {
            String text = suggestItems.get(position);
            int normal_start = text.indexOf(searchText);
            if (normal_start != -1) {
                SpannableStringBuilder spannedText = new SpannableStringBuilder();
                spannedText.append(text);
                if (normal_start == 0) {
                    spannedText.setSpan(new ForegroundColorSpan(this.color_normal), 0, searchText.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    spannedText.setSpan(new StyleSpan(Typeface.NORMAL), 0, searchText.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                    spannedText.setSpan(new ForegroundColorSpan(this.color_bold), searchText.length(), text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    spannedText.setSpan(new StyleSpan(Typeface.BOLD), searchText.length(), text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                } else {
                    spannedText.setSpan(new ForegroundColorSpan(this.color_bold), 0, normal_start, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    spannedText.setSpan(new StyleSpan(Typeface.BOLD), 0, normal_start, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                    spannedText.setSpan(new ForegroundColorSpan(this.color_normal), normal_start, normal_start + searchText.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    spannedText.setSpan(new StyleSpan(Typeface.NORMAL), normal_start, normal_start + searchText.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                    spannedText.setSpan(new ForegroundColorSpan(this.color_bold), normal_start + searchText.length(), text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    spannedText.setSpan(new StyleSpan(Typeface.BOLD), normal_start + searchText.length(), text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                }
                holder.suggest_text.setText(spannedText);
            } else {
                holder.suggest_text.setText(text);
            }
            holder.suggestlist_item_layout.setOnClickListener(view -> this.assist.updateSearchField(text, true));
            holder.suggest_arrow.setOnClickListener(view -> this.assist.updateSearchField(text, false));
        }
    }

    @Override
    public int getItemCount() {
        if (suggestItems != null)
            return suggestItems.size();
        return 0;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void notifyChange(String searchText) {
        this.searchText = searchText;
        this.notifyDataSetChanged();
    }

    static class SuggestViewHolder extends RecyclerView.ViewHolder {
        LinearLayout suggestlist_item_layout;
        TextView suggest_text;
        AppCompatImageView suggest_arrow;

        public SuggestViewHolder(View itemView) {
            super(itemView);
            suggestlist_item_layout = itemView.findViewById(R.id.suggestlist_item_layout);
            suggest_text = itemView.findViewById(R.id.suggest_text);
            suggest_arrow = itemView.findViewById(R.id.suggest_arrow);
        }
    }
}
