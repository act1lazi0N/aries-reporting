package com.actilazion.ariesreportingproject.support;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

public class ApplicationTestPropertiesInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String APPLICATION_TEST_YML = "application-test.yml";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                    .load(APPLICATION_TEST_YML, new ClassPathResource(APPLICATION_TEST_YML));
            for (int i = propertySources.size() - 1; i >= 0; i--) {
                applicationContext.getEnvironment()
                        .getPropertySources()
                        .addFirst(propertySources.get(i));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + APPLICATION_TEST_YML, e);
        }
    }
}
