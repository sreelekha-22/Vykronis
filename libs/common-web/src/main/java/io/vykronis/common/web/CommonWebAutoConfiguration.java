package io.vykronis.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Registers the shared {@link ApiExceptionHandler} for any Vykronis web
 * service that has {@code common-web} on the classpath. Picked up via
 * {@code META-INF/spring/...AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnWebApplication
@Import(ApiExceptionHandler.class)
public class CommonWebAutoConfiguration {
}
