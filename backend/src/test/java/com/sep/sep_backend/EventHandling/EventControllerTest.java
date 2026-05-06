package com.sep.sep_backend.EventHandling;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class EventControllerTest {

    @Test
    void eventControllerHatDenManuellenCreateEndpoint() throws NoSuchMethodException {
        assertThat(EventController.class.getAnnotation(RestController.class)).isNotNull();

        RequestMapping classMapping = EventController.class.getAnnotation(RequestMapping.class);
        assertThat(classMapping.value()).containsExactly("/api");

        Method method = EventController.class.getDeclaredMethod(
                "EventsManuellErstellen",
                EventRequestDTO.class
        );

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertThat(postMapping.value()).containsExactly("event/create/manual");
    }
}
