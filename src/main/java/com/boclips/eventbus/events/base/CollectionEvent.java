package com.boclips.eventbus.events.base;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionEvent extends UserEvent {

    @NonNull
    private String collectionId;
}