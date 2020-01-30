/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor.implementation;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

public class LooperExecutor extends Handler implements Executor {
    public LooperExecutor(Looper looper) {
        super(looper);
    }

    public static Executor newMainThreadExecutor() {
        return new LooperExecutor(Looper.getMainLooper());
    }

    @Override
    public void execute(Runnable command) {
        if (!post(command)) {
            throw new IllegalStateException(toString() + " post is rejected");
        }
    }
}
