package com.woc.chat.adapter;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.woc.chat.MainActivity;
import com.woc.chat.R;
import com.woc.chat.emoji.EmojiconMultiAutoCompleteTextView;
import com.woc.chat.emoji.EmojiconTextView;
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
    public static  final  int TYPE_STRANGER_RESULT=0x104;

    public static  final  String PREFIX_SYSTEM ="系统：";
    public static  final  String PREFIX_MYSELF ="我：";
    public static  final  String PREFIX_STRANGER ="陌生人：";
    public static  final  String PREFIX_SIGN ="签名：";
    private  Context context;
    private List<ChatItem> chatMsgList;
    private  int itemType;
    private Set<Integer> holderSet;
    private List<String> prefixList;
    private List<Integer> typeList;
    public  ChatAdapter(Context context,List<ChatItem> items)
    {
        this.context=context;
        chatMsgList =items;
        holderSet=new HashSet<>();
        prefixList=new ArrayList<>();
        typeList=new ArrayList<>();
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
                msgOwner= PREFIX_SYSTEM;
                msgViewHolder.msgTextView.setTextColor(Color.RED);
                typeList.add(TYPE_SYSTEM_MSG);
                break;
            case TYPE_MYSELF_MSG:
                msgOwner= PREFIX_MYSELF;
                msgViewHolder.msgTextView.setTextColor(Color.WHITE);
                typeList.add(TYPE_MYSELF_MSG);
                break;
            case TYPE_STRANGER_MSG:
                msgOwner= PREFIX_STRANGER;
                msgViewHolder.msgTextView.setTextColor(Color.GREEN);
                typeList.add(TYPE_STRANGER_MSG);
                break;
            case TYPE_STRANGER_SIGN:
                msgOwner= PREFIX_SIGN;
                msgViewHolder.msgTextView.setTextColor(0xffff69b4);
                typeList.add(TYPE_STRANGER_SIGN);
                break;
            case TYPE_STRANGER_RESULT:
                msgOwner= PREFIX_STRANGER;
                msgViewHolder.msgTextView.setTextColor(0xffffd500);
                typeList.add(TYPE_STRANGER_RESULT);
                break;
        }
        ChatItem item= chatMsgList.get(position);
        String msg=item.getMsg();
        final String[] finalMsg =new String[1] ;

        prefixList.add(msgOwner);

        finalMsg[0]=msgOwner+msg;
        chatMsgList.set(position,item);

        //是否慢点打字
        if(chatMsgList.get(position).isSlow()) {
            //这里是为了解决滚动时列表的重新加载
            if (!holderSet.contains(position))
                holderSet.add(position);
            else {
                msgViewHolder.msgTextView.setText(finalMsg[0]);
                return;
            }
            msgViewHolder.msgTextView.setText("");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < finalMsg[0].length(); i++) {
                        final int finalI = i;
                        MainActivity.thiz.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                char ch=finalMsg[0].charAt(finalI);
                                msgViewHolder.msgTextView.append(ch + "_");
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
        }
        else if(chatMsgList.get(position).isHtml())
        {
            msgViewHolder.msgTextView.setText(Html.fromHtml(finalMsg[0]));
        }
        else
        {
            msgViewHolder.msgTextView.setText(finalMsg[0]);
        }//isSlow
    }


    @Override
    public int getItemViewType(int position) {
        return chatMsgList.get(position).getItemType();
    }

    @Override
    public int getItemCount() {
        return chatMsgList.size();
    }

    /**
     * 消息
     */
    public class MsgViewHolder extends RecyclerView.ViewHolder
    {
        public EmojiconMultiAutoCompleteTextView msgTextView;
        public MsgViewHolder(View view)
        {
            super(view);
            msgTextView=(EmojiconMultiAutoCompleteTextView)view.findViewById(R.id.tv_chat_msg);
        }
    }

    /**
     * 获取消息列表
     * @return
     */
    public List<ChatItem> getChatMsgList()
    {
        return chatMsgList;
    }

    /**
     * 获取前缀列表
     * @return
     */
    public List<String> getPrefixList()
    {
        return prefixList;
    }

    /**
     * 获取消息类型列表
     * @return
     */
    public List<Integer> getTypeList()
    {
        return typeList;
    }
    /**
     * 获取set
     */
    public Set<Integer> getSet()
    {
        return holderSet;
    }
    /**
     * 左气泡
     */
    public  class LeftBubbleViewHolder extends RecyclerView.ViewHolder
    {
        public EmojiconTextView msgTextView;
        public LeftBubbleViewHolder(View view)
        {
            super(view);
            msgTextView=(EmojiconTextView)view.findViewById(R.id.tv_chat_msg);
        }
    }

    /**
     *右气泡
     */
    public  class RightBubbleViewHolder extends RecyclerView.ViewHolder
    {
        public EmojiconTextView msgTextView;
        public RightBubbleViewHolder(View view)
        {
            super(view);
            msgTextView=(EmojiconTextView)view.findViewById(R.id.tv_chat_msg);
        }
    }
}
