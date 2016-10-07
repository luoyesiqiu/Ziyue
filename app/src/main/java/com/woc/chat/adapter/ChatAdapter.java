package com.woc.chat.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.woc.chat.MainActivity;
import com.woc.chat.R;
import com.woc.chat.entity.ChatItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by zyw on 2016/9/1.
 * 主界面
 */
public class ChatAdapter extends  RecyclerView.Adapter<RecyclerView.ViewHolder> implements View.OnClickListener,View.OnLongClickListener{

    String TAG="ChatAdapter";

    public static  final  int TYPE_SYSTEM_MSG=0x100;
    public static  final  int TYPE_MYSELF_MSG=0x101;
    public static  final  int TYPE_STRANGER_MSG=0x102;
    public static  final  int TYPE_STRANGER_SIGN=0x103;
    private  Context context;
    private List<ChatItem> list;
    private  int itemType;
    private Set<Integer> holderSet;
    public  ChatAdapter(Context context,List<ChatItem> items)
    {
        this.context=context;
        list=items;
        holderSet=new HashSet<>();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder holder=null;

            View view  = LayoutInflater.from(
                    context).inflate(R.layout.chat_msg_item, parent,
                    false);
            holder = new MsgViewHolder(view);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        return holder;
    }

    public static interface OnRecyclerViewItemClickListener {
        void onItemClick(View view , int pos);
    }

    public static interface OnRecyclerViewItemLongClickListener {
        void onItemLongClick(View view,int pos);
    }
    private OnRecyclerViewItemClickListener mOnItemClickListener = null;
    private OnRecyclerViewItemLongClickListener mOnItemLongClickListener = null;

    public void setOnItemClickListener(OnRecyclerViewItemClickListener listener) {
        this.mOnItemClickListener = listener;
    }
    public void setOnItemLongClickListener(OnRecyclerViewItemLongClickListener listener) {
        this.mOnItemLongClickListener = listener;
    }
    @Override
    public void onClick(View v) {
        if (mOnItemClickListener != null) {
            //注意这里使用getTag方法获取数据
            mOnItemClickListener.onItemClick(v,(int)v.getTag());
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (mOnItemLongClickListener != null) {
            //注意这里使用getTag方法获取数据
            mOnItemLongClickListener.onItemLongClick(v,(int)v.getTag());
        }
        return true;
    }
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {

        final MsgViewHolder msgViewHolder=(MsgViewHolder) holder;
        final int msgType=getItemViewType(position);
        String msgOwner=null;
        //将数据保存在itemView的Tag中，以便点击时进行获取
        holder.itemView.setTag(position);

        switch (msgType) {
            case TYPE_SYSTEM_MSG:
                msgOwner="系统：";
                msgViewHolder.msgTextView.setTextColor(Color.RED);
                break;
            case TYPE_MYSELF_MSG:
                msgOwner="我：";
                msgViewHolder.msgTextView.setTextColor(Color.WHITE);
                break;
            case TYPE_STRANGER_MSG:
                msgOwner="陌生人：";
                msgViewHolder.msgTextView.setTextColor(Color.GREEN);
                break;
            case TYPE_STRANGER_SIGN:
                msgOwner="签名：";
                msgViewHolder.msgTextView.setTextColor(0xffff69b4);
                break;
        }
        final String finalMsg = msgOwner + list.get(position).getMsg();
        //是否慢点打字
        if(list.get(position).isSlow()) {

            //这里是为了解决滚动时列表的重新加载
            if (!holderSet.contains(position))
                holderSet.add(position);
            else {
                msgViewHolder.msgTextView.setText(finalMsg);
                return;
            }
            msgViewHolder.msgTextView.setText("");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < finalMsg.length(); i++) {
                        final int finalI = i;
                        MainActivity.thiz.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                msgViewHolder.msgTextView.append(finalMsg.charAt(finalI) + "_");

                            }
                        });
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        MainActivity.thiz.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                msgViewHolder.msgTextView.getEditableText().delete(msgViewHolder.msgTextView.length() - 1, msgViewHolder.msgTextView.length());

                            }
                        });
                    }

                }
            }).start();
        }else
        {
            msgViewHolder.msgTextView.setText(finalMsg);
        }//isSlow
    }


    @Override
    public int getItemViewType(int position) {
        return list.get(position).getItemType();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    /**
     * 消息
     */
    public class MsgViewHolder extends RecyclerView.ViewHolder
    {
        public TextView msgTextView;
        public MsgViewHolder(View view)
        {
            super(view);
            msgTextView=(TextView)view.findViewById(R.id.tv_chat_msg);
        }
    }


    /**
     * 左气泡
     */
    public  class LeftBubbleViewHolder extends RecyclerView.ViewHolder
    {
        public TextView msgTextView;
        public LeftBubbleViewHolder(View view)
        {
            super(view);
            msgTextView=(TextView)view.findViewById(R.id.tv_chat_msg);
        }
    }

    /**
     *右气泡
     */
    public  class RightBubbleViewHolder extends RecyclerView.ViewHolder
    {
        public TextView msgTextView;
        public RightBubbleViewHolder(View view)
        {
            super(view);
            msgTextView=(TextView)view.findViewById(R.id.tv_chat_msg);
        }
    }
}
