package com.lariflix.jemm.tools;

/**
 * Technical facts read from an ffprobe run. Fields are 0 when unknown.
 */
public class FfprobeResult {

    private int width;
    private int height;
    private double frameRate;
    private long bitRate;
    private long fileSize;
    private boolean hasAudio;
    private boolean audioKnown;

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public double getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(double frameRate) {
        this.frameRate = frameRate;
    }

    public long getBitRate() {
        return bitRate;
    }

    public void setBitRate(long bitRate) {
        this.bitRate = bitRate;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isHasAudio() {
        return hasAudio;
    }

    public void setHasAudio(boolean hasAudio) {
        this.hasAudio = hasAudio;
    }

    public boolean isAudioKnown() {
        return audioKnown;
    }

    public void setAudioKnown(boolean audioKnown) {
        this.audioKnown = audioKnown;
    }

    public boolean hasAnyData() {
        return width > 0 || height > 0 || frameRate > 0 || bitRate > 0 || fileSize > 0 || audioKnown;
    }
}
