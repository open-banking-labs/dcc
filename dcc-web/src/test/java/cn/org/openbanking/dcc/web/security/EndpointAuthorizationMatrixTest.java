package cn.org.openbanking.dcc.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matrix guard (ADR 0005): every request-mapped handler must declare its authority.
 *
 * <p>This is the regression test for the defect class the ADR exists to remove — "writes are the
 * default because nobody annotated the endpoint". It scans the exposure layer itself, so a newly
 * added endpoint without {@code @PreAuthorize} fails the build instead of quietly inheriting
 * {@code authenticated}.
 *
 * <p>Reads the annotations reflectively, so it needs neither a database nor a Spring context.
 */
class EndpointAuthorizationMatrixTest {

    private static final String BASE_PACKAGE = "cn.org.openbanking.dcc.web";

    @Test
    void everyHandlerDeclaresAnAuthority() throws Exception {
        List<Class<?>> scanned = controllers();
        // guard against a silently empty scan making this test vacuous
        assertThat(scanned).as("controllers discovered under " + BASE_PACKAGE).hasSizeGreaterThan(9);

        List<String> offenders = new ArrayList<>();
        for (Class<?> controller : scanned) {
            boolean classLevel = AnnotatedElementUtils.hasAnnotation(controller, PreAuthorize.class);
            for (Method method : controller.getDeclaredMethods()) {
                if (!isHandler(method) || classLevel) {
                    continue;
                }
                if (!AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(offenders)
                .as("handlers without @PreAuthorize (add the permission point from ADR 0005 §D11)")
                .isEmpty();
    }

    @Test
    void everyWriteHandlerIsGuardedBySomethingOtherThanPlainAuthentication() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isWrite(method)) {
                    continue;
                }
                String expression = expressionOf(controller, method);
                if (expression == null || expression.contains("isAuthenticated()")) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName() + " -> " + expression);
                }
            }
        }
        assertThat(offenders)
                .as("write handlers must require a permission point, not merely an authenticated caller")
                .isEmpty();
    }

    private static String expressionOf(Class<?> controller, Method method) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        PreAuthorize onClass = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        return onClass == null ? null : onClass.value();
    }

    private static boolean isHandler(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, GetMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class);
    }

    private static boolean isWrite(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class);
    }

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            found.add(Class.forName(definition.getBeanClassName()));
        }
        return found;
    }
}
