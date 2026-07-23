package com.lariflix.jemm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.lariflix.jemm.dtos.JellyfinGenreItem;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.dtos.JellyfinItemUpdate;
import com.lariflix.jemm.dtos.JellyfinProviderIds;
import com.lariflix.jemm.utils.TransformDateFormat;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;

/**
 * Posts item metadata updates directly without requiring in-memory folder trees.
 */
public class SaveItemMetadataDirect {

    private final String jellyfinInstanceUrl;
    private final String apiToken;
    private final TransformDateFormat transformDate = new TransformDateFormat();

    public SaveItemMetadataDirect(String jellyfinInstanceUrl, String apiToken) {
        this.jellyfinInstanceUrl = jellyfinInstanceUrl;
        this.apiToken = apiToken;
    }

    public int postUpdate(JellyfinItemMetadata metadata) throws IOException {
        if (metadata == null || metadata.getId() == null || metadata.getId().isEmpty()) {
            return 0;
        }

        JellyfinItemUpdate itemToUpdate = fromMetadata(metadata);
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String bodyRequestJson = ow.writeValueAsString(itemToUpdate);

        String url = jellyfinInstanceUrl.concat("Items/").concat(metadata.getId()).concat("?ApiKey=").concat(apiToken);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(bodyRequestJson))
                .setHeader("Content-type", "application/json")
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted while posting item update", ex);
        }
    }

    private JellyfinItemUpdate fromMetadata(JellyfinItemMetadata metadata) {
        JellyfinItemUpdate itemToUpdate = new JellyfinItemUpdate();
        itemToUpdate.setId(metadata.getId());
        itemToUpdate.setName(metadata.getName());
        itemToUpdate.setOriginalTitle(metadata.getOriginalTitle());
        itemToUpdate.setForcedSortName(metadata.getOriginalTitle());
        itemToUpdate.setCommunityRating(metadata.getCommunityRating());
        itemToUpdate.setCriticRating(metadata.getCriticRating());
        itemToUpdate.setIndexNumber(null);
        itemToUpdate.setAirsBeforeSeasonNumber("");
        itemToUpdate.setAirsAfterSeasonNumber("");
        itemToUpdate.setAirsBeforeEpisodeNumber("");
        itemToUpdate.setParentIndexNumber(null);
        itemToUpdate.setDisplayOrder("");
        itemToUpdate.setAlbum("");
        itemToUpdate.setOverview(metadata.getOverview());
        itemToUpdate.setStatus("");

        ArrayList<String> genres = new ArrayList<>();
        if (metadata.getGenreItems() != null) {
            for (JellyfinGenreItem genre : metadata.getGenreItems()) {
                if (genre != null && genre.getName() != null) {
                    genres.add(genre.getName());
                }
            }
        } else if (metadata.getGenres() != null) {
            genres.addAll(metadata.getGenres());
        }
        itemToUpdate.setGenres(genres);
        itemToUpdate.setTags(metadata.getTags() == null ? new ArrayList<>() : metadata.getTags());
        itemToUpdate.setStudios(metadata.getStudios() == null ? new ArrayList<>() : metadata.getStudios());
        itemToUpdate.setPremiereDate(transformDate.convertToFull(metadata.getPremiereDate()));
        itemToUpdate.setDateCreated(transformDate.convertToFull(metadata.getDateCreated()));
        itemToUpdate.setProductionYear(metadata.getProductionYear());
        itemToUpdate.setOfficialRating(metadata.getOfficialRating());
        itemToUpdate.setCustomRating(metadata.getCustomRating());
        itemToUpdate.setPeople(metadata.getPeople() == null ? new ArrayList<>() : metadata.getPeople());
        itemToUpdate.setLockData(false);
        itemToUpdate.setPreferredMetadataLanguage(metadata.getPreferredMetadataLanguage());
        itemToUpdate.setPreferredMetadataCountryCode(metadata.getPreferredMetadataCountryCode());

        JellyfinProviderIds providerID = new JellyfinProviderIds();
        providerID.setImdb("");
        providerID.setTmdb("");
        providerID.setTmdbCollection("");
        itemToUpdate.setProviderIds(providerID);
        return itemToUpdate;
    }
}
