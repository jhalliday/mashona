/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.mashona;

import org.jboss.byteman.contrib.bmunit.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.HierarchyTraversalMode;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedMethods;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

public class BMObjectAnnotationHandler implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    public static BMUnit5AbstractHandler[] HANDLERS = {
            new BMUnit5ConfigHandler(),
            new BMUnit5MultiScriptHandler(),
            new BMUnit5SingleScriptHandler(),
            new BMUnit5MultiRuleHandler(),
            new BMUnit5SingleRuleHandler()
    };

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        invokeOnTarget(context, AfterEach.class);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        invokeOnTarget(context, BeforeEach.class);
    }

    private void invokeOnTarget(ExtensionContext context, Class<? extends Annotation> annotationType) throws Exception {

        final Class<?> testClass = context.getRequiredTestClass();
        final Optional<WithBytemanFrom> optionalAnnotation = findAnnotation(testClass, WithBytemanFrom.class);
        if (optionalAnnotation.isEmpty()) {
            return;
        }

        final Class<?> targetClass = optionalAnnotation.get().source();

        for(Method method : findAnnotatedMethods(targetClass, annotationType, HierarchyTraversalMode.BOTTOM_UP)) {
            method.invoke(null);
        }
    }


    @Override
    public void beforeAll(ExtensionContext context) throws Exception {

        final Class<?> testClass = context.getRequiredTestClass();

        final Optional<WithBytemanFrom> optionalAnnotation = findAnnotation(testClass, WithBytemanFrom.class);
        if (optionalAnnotation.isEmpty()) {
            return;
        }

        final Class<?> targetClass = optionalAnnotation.get().source();

        if (BMUnit.isBMUnitVerbose()) {
            System.out.println("installing " + targetClass.getCanonicalName());
        }

        for (BMUnit5AbstractHandler handler : HANDLERS) {
            ExtensionContext dummyContext = new DummyExtensionContext(targetClass, null);
            handler.beforeAll(dummyContext);

            for (Object method : findAnnotatedMethods(targetClass, handler.getAnnotationClass(), HierarchyTraversalMode.BOTTOM_UP)) {
                ExtensionContext extensionContext = new DummyExtensionContext(testClass, (Method) method);
                handler.beforeEach(extensionContext);
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {

        final Class<?> testClass = context.getRequiredTestClass();

        final Optional<WithBytemanFrom> optionalAnnotation = findAnnotation(testClass, WithBytemanFrom.class);
        if (optionalAnnotation.isEmpty()) {
            return;
        }

        final Class<?> targetClass = optionalAnnotation.get().source();

        if (BMUnit.isBMUnitVerbose()) {
            System.out.println("uninstalling " + optionalAnnotation.get().source().getCanonicalName());
        }

        ExtensionContext dummyContext = new DummyExtensionContext(targetClass, null);
        for (int i = HANDLERS.length; i > 0; i--) {
            HANDLERS[i - 1].afterAll(dummyContext);
        }
    }

}
