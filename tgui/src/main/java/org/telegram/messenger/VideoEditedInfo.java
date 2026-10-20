package org.telegram.messenger;

/**
 * tgui: business-boundary stub. Upstream VideoEditedInfo describes a video to transcode; the
 * input bar only reads the trim range and duration of a recorded round video for its preview
 * timeline. Hosts that record round videos post it in {@code NotificationCenter.audioDidSent}.
 */
public class VideoEditedInfo {
    public long startTime;
    public long endTime;
    public long estimatedDuration;
    public String originalPath;
}
