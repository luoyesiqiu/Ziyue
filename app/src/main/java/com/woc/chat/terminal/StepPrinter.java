package com.woc.chat.terminal;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;

public abstract class StepPrinter extends Thread {
    private TerminalView mTerminalView;

    private CharSequence mText;
    private int mStart;
    private boolean mEndsWithNewLine;

    private boolean mInsert;

    private boolean mInstanceOfSpan;
    private SpanHolder[] mSpanHolders;

    private boolean mKeepGoing = true;
    private boolean mRunning = true;

    private final Object mLock = new Object();
    private boolean mLocking;

    public StepPrinter(TerminalView terminalView, CharSequence text, boolean endsWithNewLine, final CharSequence prefix) {
        mTerminalView = terminalView;

        mInsert = !mTerminalView.isWaiting();
        mEndsWithNewLine = endsWithNewLine;

        synchronized (mLock) {
            try {
                mTerminalView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mInsert) {
                            CharSequence curPrefix = mTerminalView.getCurrentPrefix();
                            mStart = mTerminalView.getLastEditablePosition() - (curPrefix == null ? 0 : curPrefix.length());
                            Log.d("---------", mTerminalView.getLastEditablePosition() + " - " + (curPrefix == null ? 0 : curPrefix.length()) + " = " + mStart);
                            if (mStart < 0)
                                mStart = 0;

                            doPrint("\n", mStart);
                        } else {
                            mStart = mTerminalView.getText().length();
                        }

                        if (prefix != null) {
                            doPrint(prefix, mStart);
                            mStart += prefix.length();
                        }

                        synchronized (mLock) {
                            Log.d("===================", "[notify in printer constructor]");
                            mLock.notify();
                        }
                    }
                });

                Log.d("===================", "[wait   in printer constructor]");
                mLock.wait();
            } catch (InterruptedException ignored) {
            }
        }

        mInstanceOfSpan = text instanceof Spanned;
        if (mInstanceOfSpan) {
            mText = text.toString();

            saveSpans((Spanned) text);
        } else {
            mText = text;
        }
    }

    private void saveSpans(Spanned spanned) {
        Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        mSpanHolders = new SpanHolder[spans.length];

        int len = spans.length;
        for (int i = 0; i < len; i++) {
            Object span = spans[i];
            SpanHolder holder = new SpanHolder();
            holder.span = span;
            holder.start = spanned.getSpanStart(span) + mStart;
            holder.end = spanned.getSpanEnd(span) + mStart;
            holder.flag = spanned.getSpanFlags(span);
            mSpanHolders[i] = holder;
            //Log.d("===================", "span[" + i + "] " + holder.span.getClass().getSimpleName() + " " + holder.start + "..." + holder.end);
        }
    }

    @Override
    public void run() {
        mRunning = true;

        int length = mText.length();
        int i = 0;

        try {
            while (i < length) {
                print(mText.subSequence(i, ++i), mStart + i - 1, false, mEndsWithNewLine && i == length);

                if (!mKeepGoing)
                    break;

                Thread.sleep(mTerminalView.getStepMills());
            }
        } catch (InterruptedException ignored) {
        }

        if (i < length)
            print(mText.subSequence(i, length), mStart + i, true, mEndsWithNewLine);

        mRunning = false;
        onFinish();
        Log.d("===================", "[finish] " + (i < length ? "(skipped) " : "") + mText);
    }

    private void print(final CharSequence text, final int firstWordPosition, final boolean skipping, final boolean appendNewLine) {
        Log.d("---------", "print " + text + " at " + firstWordPosition + (appendNewLine ? " and append new line" : ""));

        synchronized (mLock) {
            try {
                mTerminalView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mInstanceOfSpan) {
                            Editable viewText = mTerminalView.getText();

                            if (!skipping) {
                                doPrint(text, firstWordPosition);

                                for (SpanHolder holder : mSpanHolders) {
                                    if (holder.start == firstWordPosition)
                                        startSpan(viewText, holder.span, holder.start);
                                    else if (holder.end == firstWordPosition + 1)
                                        resetSpan(viewText, holder.span, holder.start, holder.end, holder.flag);
                                }
                            } else {
                                SpannableString sb = new SpannableString(text);

                                for (SpanHolder holder : mSpanHolders) {
                                    if (holder.start >= firstWordPosition)
                                        setSpan(sb, holder.span, holder.start - firstWordPosition, holder.end - firstWordPosition, holder.flag);
                                }

                                doPrint(sb, firstWordPosition);

                                for (SpanHolder holder : mSpanHolders) {
                                    if (holder.start < firstWordPosition) {
                                        resetSpan(viewText, holder.span, holder.start, holder.end, holder.flag);
                                    }
                                }
                            }
                        } else {
                            doPrint(text, firstWordPosition);
                        }

                        if (appendNewLine && !mInsert) {
                            doPrint("\n", -1); //printStep("\n");

                            mTerminalView.updateWaitingState();

                            if (!mTerminalView.isWaiting())
                                doPrint(mTerminalView.getPrefix(), -1); //printStep(mPrefix);
                        }

                        synchronized (mLock) {
                            Log.d("===================", "[notify in printer]");
                            mLock.notify();
                            mLocking = false;
                        }
                    }
                });

                Log.d("===================", "[wait   in printer]");
                mLocking = true;
                mLock.wait();
            } catch (InterruptedException ignored) {
                mLocking = false;
            }
        }
    }

    private void doPrint(final CharSequence text, final int where) {
        mTerminalView.startEdit();

        if (mInsert) {
            mTerminalView.getText().insert(where, text);
        } else {
            mTerminalView.append(text);
        }

        mTerminalView.finishEdit();
    }

    private void startSpan(Spannable text, Object span, int start) {
        //Log.d("===================", "span start at " + start);
        text.setSpan(span, start, start + 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
    }

    private void resetSpan(Spannable text, Object span, int start, int end, int flag) {
        //Log.d("===================", "span remove");
        text.removeSpan(span);
        //Log.d("===================", "span reset at " + start + "..." + end);
        text.setSpan(span, start, end, flag);
    }

    private void setSpan(Spannable text, Object span, int start, int end, int flag) {
        //Log.d("===================", "span set at " + start + "..." + end);
        text.setSpan(span, start, end, flag);
    }

    protected void skip() {
        Log.d("===================", "[skip  ] " + mText);
        mKeepGoing = false;
        if (!mLocking)
            interrupt();
    }

    protected boolean isRunning() {
        return mRunning;
    }

    protected abstract void onFinish();

    private class SpanHolder {
        public Object span;
        public int start, end, flag;
    }
}
