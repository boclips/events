package com.boclips.events.types.video;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideosInclusionInDownloadRequested {

    @NonNull
    private List<String> videoIds;
}