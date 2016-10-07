package com.woc.chat.terminal;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

/**
 * Description:
 * User: Pencil
 * Date: 2016/9/21 0021
 */
public class TerminalView extends EditText {
    public static final int STEP_MILLS = 100;
    public static final String PREFIX = ">";

    private volatile CharSequence mPrefix = PREFIX;
    private volatile int mStepMills = STEP_MILLS;

    private boolean mWaiting;
    private boolean mAboutToStopWaiting;

    private volatile boolean mEditable = true;
    private boolean mWasEditableBeforeSelectable = mEditable;
    private boolean mShouldBeEditableAfterWaiting = mEditable;
    private volatile int mLastEditablePosition;

    //Displaced by "LongClickable"
    //private boolean mSelectable;

    private boolean mTextEditProgrammaticallyFlag;
    private boolean mTextReplaceFlag;

    private CharSequence mLastSubmission;

    private OnSubmitListener mOnSubmitListener;

    private Controller mController;

    private InputFilter mInputFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            boolean preventTextChanging = false;

            if (mTextEditProgrammaticallyFlag) {
                int changedLength = mTextReplaceFlag ? end - start - dend + dstart : end - start;

                if (mWaiting)
                    mLastEditablePosition = getText().length() + changedLength;
                else
                    mLastEditablePosition += changedLength;
                Log.d("-----------", "mLastEditablePosition " + mLastEditablePosition);
                //Log.d("------------------", "filter " + source + " " + start + " " + end + " " + dest + " " + dstart + " " + dend + " len: " + changedLength);
            } else {
                if (mEditable) {
                    if (source.length() == 0 /* "delete" action */ && getSelectionStart() <= mLastEditablePosition) {
                        preventTextChanging = true;
                    } else if (mController.skip()) {
                        preventTextChanging = true;
                    }
                } else {
                    preventTextChanging = true;
                }
            }

            return preventTextChanging ? dest.subSequence(dstart, dend) : null;
        }
    };

    private OnKeyListener mOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (!mEditable)
                return true;

            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (!mController.skip()) {
                        Editable text = getText();
                        mLastSubmission = text.subSequence(mLastEditablePosition, text.length());

                        startWaiting();

                        postLine(null, false, false);

                        onSubmit();
                    }
                }
                return true;
            }
            return false;
        }
    };

    public TerminalView(Context context) {
        this(context, null);
    }

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TerminalView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TerminalView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mController = new Controller(this);
        mController.start();

        mLastEditablePosition = mPrefix.length();
        setText(SpannableString.valueOf(mPrefix));

        setFilters(new InputFilter[]{mInputFilter});
        setOnKeyListener(mOnKeyListener);
    }

    public void postText(CharSequence text, boolean stepping) {
        printText(text, stepping, false, false);
    }

    public void postColoredText(CharSequence text, int color, boolean stepping) {
        SpannableString sb = SpannableString.valueOf(text);
        sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        printText(sb, stepping, false, false);
    }

    public void postLine(CharSequence text, boolean stepping, boolean showPrefix) {
        printText(text, stepping, true, showPrefix);
    }

    public void postColoredLine(CharSequence text, int color, boolean stepping, boolean showPrefix) {
        SpannableString sb = SpannableString.valueOf(text);
        sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        printText(sb, stepping, true, showPrefix);
    }

    private void printText(final CharSequence text, final boolean stepping, final boolean endsWithNewLine, final boolean showPrefix) {
        if (text != null && text.length() != 0) {

            if (stepping) {
                mController.postTask(new Controller.Task() {
                    @Override
                    public void exec() {
                        mController.createPrinter(text, endsWithNewLine, showPrefix);
                    }

                    @Override
                    public String getName() {
                        return "<Create printer " + text + ">";
                    }
                });
            } else {
                mController.postTask(new Controller.Task() {
                    @Override
                    public void exec() {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                mTextEditProgrammaticallyFlag = true;

                                boolean insert = !mWaiting;

                                if (insert) {
                                    Editable viewText = getText();
                                    int position = mLastEditablePosition - mPrefix.length();
                                    if (position < 0)
                                        position = 0;

                                    viewText.insert(position, "\n");

                                    if (showPrefix)
                                        viewText.insert(position, mPrefix);

                                    viewText.insert(position, text);
                                } else {
                                    append(text);
                                }

                                mTextEditProgrammaticallyFlag = false;
                            }
                        });
                    }

                    @Override
                    public String getName() {
                        return "<Do print (skip) " + text + ">";
                    }
                });
            }
        } else if (endsWithNewLine) {
            printText("\n", false, false, false);
        }
    }

    private void replace(CharSequence src, int start, int end) {
        mTextEditProgrammaticallyFlag = true;
        mTextReplaceFlag = true;
        getText().replace(start, end, src);
        mTextReplaceFlag = false;
        mTextEditProgrammaticallyFlag = false;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (!mTextEditProgrammaticallyFlag && !isSelectable()) {
            if (selStart < mLastEditablePosition) {
                if (selEnd >= mLastEditablePosition)
                    setSelection(mLastEditablePosition, selEnd);
                else
                    setSelection(mLastEditablePosition);
            }
        }
        super.onSelectionChanged(mLastEditablePosition, selEnd);
    }

    private void onSubmit() {
        OnSubmitListener onSubmitListener = getOnSubmitListener();
        if (onSubmitListener != null) {
            onSubmitListener.onSubmit(mLastSubmission);
        }
    }

    private void startWaiting() {
        mWaiting = true;
        mShouldBeEditableAfterWaiting = mEditable;
        mEditable = false;
    }

    private void stopWaiting() {
        mWaiting = false;
        mEditable = mShouldBeEditableAfterWaiting;
    }

    protected void updateWaitingState() {
        if (mAboutToStopWaiting) {
            Log.d(">>>>>>>>>", "[stop waiting]");
            mAboutToStopWaiting = false;
            stopWaiting();
        }
    }

    public boolean isWaiting() {
        return mWaiting;
    }

    public void finish() {
        if (mWaiting) {
            if (mController.hasTask() || mController.isRunning()) {
                Log.d(">>>>>>>>>", "[be about to stop waiting]");
                mAboutToStopWaiting = true;
            } else {
                Log.d(">>>>>>>>>", "[instantly stop waiting]");
                stopWaiting();

                if (!mController.isRunning()) {
                    mController.postTask(new Controller.Task() {
                        @Override
                        public void exec() {
                            final Object lock = new Object();

                            synchronized (lock) {
                                try {
                                    post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mTextEditProgrammaticallyFlag = true;
                                            append(mPrefix);
                                            mTextEditProgrammaticallyFlag = false;

                                            synchronized (lock) {
                                                Log.d("===================", "[notify finish]");
                                                lock.notify();
                                            }
                                        }
                                    });

                                    Log.d("===================", "[wait   finish]");
                                    lock.wait();
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }

                        @Override
                        public String getName() {
                            return "<Finish with prefix>";
                        }
                    });
                }
            }
        }
    }

    public void recoverLastSubmission() {
        printText(mLastSubmission, false, false, false);
    }

    protected void startEdit() {
        mTextEditProgrammaticallyFlag = true;
    }

    protected void finishEdit() {
        mTextEditProgrammaticallyFlag = false;
    }

    protected int getLastEditablePosition() {
        return mLastEditablePosition;
    }

    public boolean isEditable() {
        return mEditable;
    }

    public void setEditable(boolean editable) {
        mWasEditableBeforeSelectable = mEditable = editable;
    }

    public boolean isSelectable() {
        return isLongClickable();
    }

    public void setSelectable(boolean selectable) {
        if (selectable) {
            mWasEditableBeforeSelectable = mEditable;
            mShouldBeEditableAfterWaiting = false;
            mEditable = false;
        } else {
            mEditable = mWasEditableBeforeSelectable;
            mShouldBeEditableAfterWaiting = mEditable;
        }

        setLongClickable(selectable);
    }

    public OnSubmitListener getOnSubmitListener() {
        return mOnSubmitListener;
    }

    public void setOnSubmitListener(OnSubmitListener onSubmitListener) {
        mOnSubmitListener = onSubmitListener;
    }

    public int getStepMills() {
        return mStepMills;
    }

    public void setStepMills(int stepMills) {
        mStepMills = stepMills;
    }

    public CharSequence getPrefix() {
        return mPrefix;
    }

    public void setPrefix(CharSequence prefix) {
        final int oldLength = mPrefix.length();
        mPrefix = prefix;

        if (!mWaiting) {
            mController.postTask(new Controller.Task() {
                @Override
                public void exec() {
                    final Controller.Task thiz = this;
                    synchronized (this) {
                        try {
                            mTextEditProgrammaticallyFlag = true;
                            post(new Runnable() {
                                @Override
                                public void run() {
                                    replace(mPrefix, mLastEditablePosition - oldLength, mLastEditablePosition);

                                    synchronized (thiz) {
                                        thiz.notify();
                                    }
                                }
                            });
                            mTextEditProgrammaticallyFlag = false;

                            wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                @Override
                public String getName() {
                    return "Replace " + mPrefix;
                }
            });
        }
    }

    public void release() {
        mController.release();
    }

    public interface OnSubmitListener {
        void onSubmit(CharSequence text);
    }
}
