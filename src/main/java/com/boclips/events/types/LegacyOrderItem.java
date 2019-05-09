package com.boclips.events.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

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
    private Date dateUpdated;

    @NonNull
    @JsonProperty("date_created")
    private Date dateCreated;

    @NonNull
    private LegacyOrderItemLicense license;

    @NonNull
    private BigDecimal price;

    @NonNull
    @JsonProperty("transcripts_required")
    private Boolean transcriptsRequired;

    @NonNull
    private String status;
}
