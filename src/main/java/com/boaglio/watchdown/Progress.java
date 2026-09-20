package com.boaglio.watchdown;

/**
 * Where a long step reports how far along it is.
 *
 * <p>Steps call this while they work; what the user sees is up to the reporter, which draws a live
 * bar on a terminal and stays quiet when the output is a pipe.
 */
public interface Progress {

    /** A step that reports nowhere, for tests and for code paths with nothing to report. */
    Progress NONE = new Progress() {
        @Override
        public void fraction(double fraction) {
        }

        @Override
        public void note(String note) {
        }
    };

    /** How much of the step is done, from 0 to 1. Values outside that range mean "unknown". */
    void fraction(double fraction);

    /** A short changing detail, such as {@code 3/12}, shown next to the bar. */
    void note(String note);
}
