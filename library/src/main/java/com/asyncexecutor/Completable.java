package com.asyncexecutor;

/**
 * This interface describes a type that provides a way to complete some operation manually.
 *
 * @param <T> type of result
 */
public interface Completable<T> {
    /**
     * Complete operation successfully
     *
     * @param result value or null
     * @return <code>true<code/> is operation status is changed, <code>false<code/> if operation can
     * not be completed or has been already completed.
     */
    boolean complete(T result);

    /**
     * Complete operation with error
     *
     * @param e error
     * @return <code>true<code/> is operation status is changed, <code>false<code/> if operation can
     * not be completed or has been already completed.
     */
    boolean complete(Exception e);

    /**
     * Getter that returns type of operation
     *
     * @return <code>true<code/> if operation can be completed multiple times or <code>false<code/>
     * if operation can be completed only a once
     */
    boolean isMultiCompletable();
}
