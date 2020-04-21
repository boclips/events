package com.boclips.eventbus.domain.user;

import com.boclips.eventbus.domain.Subject;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @NonNull
    private String id;

    @Deprecated
    private String firstName;

    @Deprecated
    private String lastName;

    private String email;

    @Deprecated
    @NonNull
    private List<Subject> subjects;

    @Deprecated
    @NonNull
    private List<Integer> ages;

    private Organisation organisation;

    @NonNull
    private Boolean isBoclipsEmployee;

    @Deprecated
    private String role;

    @NonNull
    private UserProfile profile;
}
