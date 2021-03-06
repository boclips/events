package com.boclips.eventbus.events.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegacyOrderItem {
    @NonNull
    private String id;

    @NonNull
    @JsonProperty("uuid")
    private String uuid;

    @NonNull
    @JsonProperty("asset_id")
    private String assetId;

    @NonNull
    @JsonProperty("date_updated")
    private ZonedDateTime dateUpdated;

    @NonNull
    @JsonProperty("date_created")
    private ZonedDateTime dateCreated;

    @NonNull
    @JsonProperty("transcripts_required")
    private Boolean transcriptsRequired;

    @NonNull
    private String status;

    @NonNull
    private String trimming;
}
