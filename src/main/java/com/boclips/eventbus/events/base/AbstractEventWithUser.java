package com.boclips.eventbus.events.base;

import com.boclips.eventbus.domain.user.User;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class AbstractEventWithUser extends AbstractEvent {

    @NonNull
    private User user;
}
