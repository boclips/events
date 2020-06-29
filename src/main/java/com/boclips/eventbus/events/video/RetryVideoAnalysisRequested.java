package com.boclips.eventbus.events.video;

import com.boclips.eventbus.BoclipsEvent;
import lombok.*;

import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@BoclipsEvent("retry-video-analysis-requested")
public class RetryVideoAnalysisRequested {

    @NonNull
    private String videoId;

    @NonNull
    private String videoUrl;

    private Locale language;
}
