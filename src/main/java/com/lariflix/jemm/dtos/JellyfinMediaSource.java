package com.lariflix.jemm.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;

/**
 * A media source entry from Jellyfin item metadata.
 */
public class JellyfinMediaSource {

    @JsonProperty("Id")
    public String id;
    @JsonProperty("Path")
    public String path;
    @JsonProperty("Container")
    public String container;
    @JsonProperty("Size")
    public Long size;
    @JsonProperty("Bitrate")
    public Long bitrate;
    @JsonProperty("MediaStreams")
    public ArrayList<JellyfinMediaStream> mediaStreams;

    public JellyfinMediaSource() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getBitrate() {
        return bitrate;
    }

    public void setBitrate(Long bitrate) {
        this.bitrate = bitrate;
    }

    public ArrayList<JellyfinMediaStream> getMediaStreams() {
        return mediaStreams;
    }

    public void setMediaStreams(ArrayList<JellyfinMediaStream> mediaStreams) {
        this.mediaStreams = mediaStreams;
    }
}
