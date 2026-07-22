package com.springmsa.adminbff.registration.controller;

import com.springmsa.adminbff.registration.service.AdminBffRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminBffRegistrationControllerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AdminBffRegistrationService.class, () -> mock(AdminBffRegistrationService.class))
            .withUserConfiguration(AdminBffRegistrationController.class);

    @Test
    void doesNotRegisterControllerWhenFlagIsMissing() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(AdminBffRegistrationController.class));
    }

    @Test
    void doesNotRegisterControllerWhenFlagIsFalse() {
        contextRunner
                .withPropertyValues("admin-bff.registration.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AdminBffRegistrationController.class));
    }

    @Test
    void registersControllerOnlyWhenFlagIsTrue() {
        contextRunner
                .withPropertyValues("admin-bff.registration.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(AdminBffRegistrationController.class));
    }
}
