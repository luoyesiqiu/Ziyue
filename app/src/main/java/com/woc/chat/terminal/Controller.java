package com.woc.chat.terminal;

import android.util.Log;

import java.util.ArrayList;

public class Controller extends Thread {
    private TerminalView mTerminalView;
    private StepPrinter mStepPrinter;

    private boolean mLoop = true;
    private final Object mLoopLock = new Object();
    private final Object mTaskLock = new Object();
    private final Object mTextEditLock = new Object();

    private ArrayList<Task> mTasks;

    public Controller(TerminalView terminalView) {
        mTerminalView = terminalView;
        mTasks = new ArrayList<>();
    }

    @Override
    public void run() {
        while (mLoop) {
            while (!mTasks.isEmpty()) {
                Task task = mTasks.get(0);
                Log.d("===================", "[exec  ] " + task.getName());
                if (isRunning()) {
                    synchronized (mTaskLock) {
                        try {
                            mStepPrinter.skip();
                            Log.d("===================", "[wait   task  ]");
                            mTaskLock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                task.exec();

                Log.d("===================", "[edexec] " + task.getName());

                if (!task.isPrinterTask() && mTerminalView.updateWaitingState()) {
                    synchronized (mTextEditLock) {
                        try {
                            Log.d(">>>>>>>>>", "print prefix after waiting");
                            mTerminalView.post(new Runnable() {
                                @Override
                                public void run() {
                                    mTerminalView.startEdit();
                                    CharSequence prefix = mTerminalView.getPrefix();
                                    mTerminalView.append(prefix);
                                    mTerminalView.setCurrentPrefix(prefix);
                                    mTerminalView.finishEdit();

                                    synchronized (mTextEditLock) {
                                        Log.d("===================", "[notify edit  ]");
                                        mTextEditLock.notify();
                                    }
                                }
                            });
                            Log.d("===================", "[wait   edit  ]");
                            mTextEditLock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                mTasks.remove(task);
            }

            synchronized (mLoopLock) {
                try {
                    Log.d("===================", "[wait   looper]");
                    mLoopLock.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    protected boolean isRunning() {
        return mStepPrinter != null && mStepPrinter.isRunning();
    }

    protected void createPrinter(CharSequence text, boolean endsWithNewLine, CharSequence prefix) {
        mStepPrinter = new StepPrinter(mTerminalView, text, endsWithNewLine, prefix) {
            @Override
            protected void onFinish() {
                onTaskFinish();
            }
        };
        mStepPrinter.start();
    }

    protected synchronized boolean skip() {
        boolean running = isRunning();

        if (running)
            postTask(new Task() {
                @Override
                public void exec() {
                }

                @Override
                public boolean isPrinterTask() {
                    return false;
                }

                @Override
                public String getName() {
                    return "<Skip>";
                }
            });

        return running;
    }

    protected boolean postTask(Task task) {
        pushTask(task, currentThread() == this);

        return isRunning();
    }

    private synchronized void pushTask(Task task, boolean prior) {
        Log.d("===================", "[push  ] " + (prior ? "prior " : "") + (task == null ? "null" : task.getName()));
        if (prior)
            mTasks.add(0, task);
        else
            mTasks.add(task);

        if (mTasks.size() == 1)
            synchronized (mLoopLock) {
                Log.d("===================", "[notify looper] from empty queue");
                mLoopLock.notify();
            }
    }

    protected boolean hasTask() {
        return !mTasks.isEmpty();
    }

    private void onTaskFinish() {
        synchronized (mTaskLock) {
            Log.d("===================", "[notify task  ]");
            mTaskLock.notify();
        }
    }

    protected void release() {
        mLoop = false;
        synchronized (mTaskLock) {
            Log.d("===================", "[notify task  ] release");
            mTaskLock.notify();
        }
        synchronized (mLoopLock) {
            Log.d("===================", "[notify looper] release");
            mLoopLock.notify();
        }
    }

    protected interface Task {
        void exec();

        boolean isPrinterTask();

        String getName();
    }
}