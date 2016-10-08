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

public class TerminalView extends EditText {
    public static final int STEP_MILLS = 100;
    public static final String PREFIX = "";

    private volatile CharSequence mPrefix = PREFIX;
    private volatile CharSequence mCurrentPrefix = PREFIX;
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
                Log.d("---------", "filter " + source + ", at " + dstart + ", mLastEditablePosition " + mLastEditablePosition);
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

                        printText(null, false, true, null);

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
        printText(text, stepping, false, null);
    }

    public void postColoredText(CharSequence text, int color, boolean stepping) {
        SpannableString sb = SpannableString.valueOf(text);
        sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        printText(sb, stepping, false, null);
    }

    public void postLine(CharSequence text, boolean stepping, CharSequence prefix) {
        printText(text, stepping, true, prefix);
    }

    public void postColoredLine(CharSequence text, int color, boolean stepping, CharSequence prefix) {
        SpannableString sb = SpannableString.valueOf(text);
        sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        printText(sb, stepping, true, prefix);
    }

    private void printText(final CharSequence text, final boolean stepping, final boolean endsWithNewLine, final CharSequence prefix) {
        if (text != null && text.length() != 0) {
            if (stepping) {
                mController.postTask(new Controller.Task() {
                    @Override
                    public void exec() {
                        mController.createPrinter(text, endsWithNewLine, prefix);
                    }

                    @Override
                    public boolean isPrinterTask() {
                        return true;
                    }

                    @Override
                    public String getName() {
                        return "<Create printer " + (prefix == null ? "" : prefix) + text + ">";
                    }
                });
            } else {
                mController.postTask(new Controller.Task() {
                                         @Override
                                         public void exec() {
                                             final Object textEditLock = new Object();

                                             synchronized (textEditLock) {
                                                 try {
                                                     post(new Runnable() {
                                                         @Override
                                                         public void run() {
                                                             mTextEditProgrammaticallyFlag = true;

                                                             boolean insert = !mWaiting;

                                                             if (insert) {
                                                                 Editable viewText = getText();
                                                                 int position = mLastEditablePosition - (mCurrentPrefix == null ? 0 : mCurrentPrefix.length());
                                                                 Log.d("---------", mLastEditablePosition + " - " + (mCurrentPrefix == null ? 0 : mCurrentPrefix.length()) + " = " + position);
                                                                 if (position < 0)
                                                                     position = 0;

                                                                 Log.d("---------", "instantly insert " + text + " at " + position + (endsWithNewLine ? " and append new line" : ""));
                                                                 viewText.insert(position, "\n");

                                                                 if (prefix != null) {
                                                                     viewText.insert(position, prefix);
                                                                     position += prefix.length();
                                                                 }

                                                                 viewText.insert(position, text);
                                                             } else {
                                                                 Log.d("---------", "instantly append " + text + (endsWithNewLine ? " and append new line" : ""));
                                                                 if (prefix != null)
                                                                     append(prefix);

                                                                 append(text);
                                                             }

                                                             if (endsWithNewLine && !insert)
                                                                 append("\n");

                                                             mTextEditProgrammaticallyFlag = false;

                                                             synchronized (textEditLock) {
                                                                 textEditLock.notify();
                                                             }
                                                         }
                                                     });

                                                     textEditLock.wait();
                                                 } catch (InterruptedException ignored) {
                                                 }
                                             }
                                         }

                                         @Override
                                         public boolean isPrinterTask() {
                                             return false;
                                         }

                                         @Override
                                         public String getName() {
                                             return "<Do print (skip) " + (prefix == null ? "" : prefix) + text + ">";
                                         }
                                     }

                );
            }
        } else if (endsWithNewLine)

        {
            printText("\n", false, false, null);
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

    public void startWaiting() {
        mWaiting = true;
        mShouldBeEditableAfterWaiting = mEditable;
        mEditable = false;
    }

    public void stopWaiting() {
        mWaiting = false;
        mEditable = mShouldBeEditableAfterWaiting;
    }

    protected boolean updateWaitingState() {
        if (mAboutToStopWaiting) {
            Log.d(">>>>>>>>>", "stop waiting");
            mAboutToStopWaiting = false;
            stopWaiting();

            return true;
        }
        return false;
    }

    public boolean isWaiting() {
        return mWaiting;
    }

    public void finish() {
        if (mWaiting) {
            if (mController.hasTask() || mController.isRunning()) {
                Log.d(">>>>>>>>>", "be about to stop waiting");
                mAboutToStopWaiting = true;
            } else {
                Log.d(">>>>>>>>>", "instantly stop waiting");
                stopWaiting();

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
                                        mCurrentPrefix = mPrefix;
                                        mTextEditProgrammaticallyFlag = false;

                                        synchronized (lock) {
                                            Log.d("===================", "[notify in finish]");
                                            lock.notify();
                                        }
                                    }
                                });

                                Log.d("===================", "[wait   in finish]");
                                lock.wait();
                                Log.d("===================", "[wait2   in finish]");
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }

                    @Override
                    public boolean isPrinterTask() {
                        return false;
                    }

                    @Override
                    public String getName() {
                        return "<Finish with prefix>";
                    }
                });
            }
        }
    }

    public void restoreLastSubmission() {
        printText(mLastSubmission, false, false, null);
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

    public CharSequence getCurrentPrefix() {
        return mCurrentPrefix;
    }

    protected void setCurrentPrefix(CharSequence currentPrefix) {
        mCurrentPrefix = currentPrefix;
    }

    public CharSequence getPrefix() {
        return mPrefix;
    }

    public void setPrefix(CharSequence prefix) {
        setPrefix(prefix, false);
    }

    public void setPrefix(final CharSequence prefix, boolean instant) {
        if (mWaiting) {
            mPrefix = prefix;
        } else if (instant || !(mController.hasTask() || mController.isRunning())) {
            mController.postTask(new Controller.Task() {
                @Override
                public void exec() {
                    final Object lock = new Object();

                    synchronized (lock) {
                        try {
                            post(new Runnable() {
                                @Override
                                public void run() {
                                    int offset = mCurrentPrefix == null ? 0 : mCurrentPrefix.length();
                                    replace(prefix, mLastEditablePosition - offset, mLastEditablePosition);

                                    mCurrentPrefix = prefix;
                                    mPrefix = prefix;

                                    synchronized (lock) {
                                        lock.notify();
                                    }
                                }
                            });

                            lock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                @Override
                public boolean isPrinterTask() {
                    return false;
                }

                @Override
                public String getName() {
                    return "Replace " + prefix;
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
