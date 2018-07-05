// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.core;

import com.yahoo.jdisc.handler.CompletionHandler;

import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class CompletionHandlers {

    public static void tryComplete(CompletionHandler handler) {
        if (handler == null) {
            return;
        }
        try {
            handler.completed();
        } catch (Exception e) {
            // ignore
        }
    }

    public static void tryFail(CompletionHandler handler, Throwable t) {
        if (handler == null) {
            return;
        }
        try {
            handler.failed(t);
        } catch (Exception e) {
            // ignore
        }
    }

    public static CompletionHandler wrap(CompletionHandler... handlers) {
        return wrap(Arrays.asList(handlers));
    }

    public static CompletionHandler wrap(final Iterable<CompletionHandler> handlers) {
        return new CompletionHandler() {

            @Override
            public void completed() {
                for (CompletionHandler handler : handlers) {
                    tryComplete(handler);
                }
            }

            @Override
            public void failed(Throwable t) {
                for (CompletionHandler handler : handlers) {
                    tryFail(handler, t);
                }
            }
        };
    }
}
