package com.boaglio.watchdown.download;

import com.boaglio.watchdown.WatchdownException;
import com.boaglio.watchdown.cli.ExitCode;

/** yt-dlp could not fetch the metadata or the audio. */
public class DownloadException extends WatchdownException {

    public DownloadException(String message) {
        super(ExitCode.DOWNLOAD_FAILED, message);
    }

    public DownloadException(String message, Throwable cause) {
        super(ExitCode.DOWNLOAD_FAILED, message, cause);
    }
}
