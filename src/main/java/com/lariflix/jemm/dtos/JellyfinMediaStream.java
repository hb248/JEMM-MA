package com.lariflix.jemm.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A media stream entry from Jellyfin MediaSources/MediaStreams.
 */
public class JellyfinMediaStream {

    @JsonProperty("Type")
    public String type;
    @JsonProperty("Codec")
    public String codec;
    @JsonProperty("Width")
    public Integer width;
    @JsonProperty("Height")
    public Integer height;
    @JsonProperty("AverageFrameRate")
    public Double averageFrameRate;
    @JsonProperty("RealFrameRate")
    public Double realFrameRate;
    @JsonProperty("BitRate")
    public Long bitRate;
    @JsonProperty("AspectRatio")
    public String aspectRatio;
    @JsonProperty("IsDefault")
    public boolean isDefault;

    public JellyfinMediaStream() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Double getAverageFrameRate() {
        return averageFrameRate;
    }

    public void setAverageFrameRate(Double averageFrameRate) {
        this.averageFrameRate = averageFrameRate;
    }

    public Double getRealFrameRate() {
        return realFrameRate;
    }

    public void setRealFrameRate(Double realFrameRate) {
        this.realFrameRate = realFrameRate;
    }

    public Long getBitRate() {
        return bitRate;
    }

    public void setBitRate(Long bitRate) {
        this.bitRate = bitRate;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public boolean isIsDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
