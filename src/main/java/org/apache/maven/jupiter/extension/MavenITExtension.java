package org.apache.maven.jupiter.extension;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.jupiter.extension.AnnotationHelper.getActiveProfiles;
import static org.apache.maven.jupiter.extension.AnnotationHelper.getGoals;
import static org.apache.maven.jupiter.extension.AnnotationHelper.hasActiveProfiles;
import static org.apache.maven.jupiter.extension.AnnotationHelper.isDebug;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.maven.jupiter.extension.maven.MavenCache;
import org.apache.maven.jupiter.extension.maven.MavenCacheResult;
import org.apache.maven.jupiter.extension.maven.MavenExecutionResult;
import org.apache.maven.jupiter.extension.maven.MavenExecutionResult.ExecutionResult;
import org.apache.maven.jupiter.extension.maven.MavenLog;
import org.apache.maven.jupiter.extension.maven.MavenProjectResult;
import org.apache.maven.jupiter.extension.maven.ProjectHelper;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

/**
 * @author Karl Heinz Marbaise
 */
public class MavenITExtension implements BeforeEachCallback, BeforeAllCallback, TestInstancePostProcessor,
    ParameterResolver, BeforeTestExecutionCallback, AfterTestExecutionCallback, AfterAllCallback {

  private static final Namespace NAMESPACE_MAVEN_IT = Namespace.create(MavenITExtension.class);

  private static final String TARGET_DIRECTORY = "TARGET_DIRECTORY";

  private Optional<MavenIT> findMavenIt(ExtensionContext context) {
    Optional<ExtensionContext> current = Optional.of(context);
    while (current.isPresent()) {
      Optional<MavenIT> endToEndTest = findAnnotation(current.get().getRequiredTestClass(), MavenIT.class);
      if (endToEndTest.isPresent()) {
        return endToEndTest;
      }
      current = current.get().getParent();
    }
    return Optional.empty();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    Class<?> testClass = context.getTestClass()
        .orElseThrow(() -> new ExtensionConfigurationException("MavenITExtension is only supported for classes."));

    //FIXME: Need to reconsider the maven-it directory?
    File baseDirectory = new File(DirectoryHelper.getTargetDir(), "maven-it");
    String toFullyQualifiedPath = DirectoryHelper.toFullyQualifiedPath(testClass.getPackage(),
        testClass.getSimpleName());

    File mavenItBaseDirectory = new File(baseDirectory, toFullyQualifiedPath);
    mavenItBaseDirectory.mkdirs();

    Store store = context.getStore(NAMESPACE_MAVEN_IT);
    store.put(Result.BaseDirectory, mavenItBaseDirectory);
    store.put(TARGET_DIRECTORY, DirectoryHelper.getTargetDir());
  }

  @Override
  public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    List<Class<?>> availableTypes = Arrays.asList(MavenExecutionResult.class, MavenLog.class, MavenCacheResult.class,
        MavenProjectResult.class);
    return availableTypes.contains(parameterContext.getParameter().getParameterizedType());
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {

    Store nameSpace = extensionContext.getStore(NAMESPACE_MAVEN_IT);

    Result result = Stream.of(Result.values())
        .filter(s -> s.getKlass().equals(parameterContext.getParameter().getType()))
        .findFirst()
        .orElseGet(() -> Result.BaseDirectory);

    return nameSpace.get(result + extensionContext.getUniqueId(), result.getKlass());
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) throws IOException, InterruptedException {
    AnnotatedElement annotatedElement = context.getElement()
        .orElseThrow(() -> new IllegalStateException("MavenIT Annotation not found."));

    Store nameSpace = context.getStore(NAMESPACE_MAVEN_IT);
    File mavenItBaseDirectory = nameSpace.get(Result.BaseDirectory, File.class);
    File targetDirectory = nameSpace.get(TARGET_DIRECTORY, File.class);

    Method methodName = context.getTestMethod().orElseThrow(() -> new IllegalStateException("No method given"));

    Class<?> testClass = context.getTestClass().orElseThrow(() -> new IllegalStateException("Test class not found."));
    MavenIT mavenIT = testClass.getAnnotation(MavenIT.class);

    File integrationTestCaseDirectory = new File(mavenItBaseDirectory, methodName.getName());
    integrationTestCaseDirectory.mkdirs();

    File cacheDirectory = new File(integrationTestCaseDirectory, ".m2/repository");
    if (MavenCache.Global.equals(mavenIT.mavenCache())) {
      cacheDirectory = new File(mavenItBaseDirectory, ".m2/repository");
    }
    cacheDirectory.mkdirs();

    File projectDirectory = new File(integrationTestCaseDirectory, "project");
    projectDirectory.mkdirs();

    String toFullyQualifiedPath = DirectoryHelper.toFullyQualifiedPath(testClass.getPackage(),
        testClass.getSimpleName());

    //FIXME: Copy artifacts from maven-invoker-plugin:install location into each cache HARD CODED!!
    FileUtils.copyDirectory(new File(targetDirectory, "invoker-repo"), cacheDirectory);

    //FIXME: Removed hard coded parts.
    File mavenItsBaseDirectory = new File(DirectoryHelper.getTargetDir(), "test-classes/maven-its");
    File copyMavenPluginProject = new File(mavenItsBaseDirectory, toFullyQualifiedPath + "/" + methodName.getName());
    FileUtils.copyDirectory(copyMavenPluginProject, projectDirectory);


    String mavenHome = System.getProperty("maven.home");
    if (mavenHome == null || mavenHome.isEmpty()) {
      //FIXME: currently not set; using hard coded path? Need to reconsider how to set it?
      mavenHome = "/Users/khmarbaise/tools/maven";
    }
    //FIXME: Very likely we need to tweak for Windows environment? see maven-invoker how to find Maven executable?
    String mvnLocation = mavenHome + "/bin/mvn";

    //FIXME: Removed hard coded parts.
    ApplicationExecutor mavenExecutor = new ApplicationExecutor(projectDirectory, integrationTestCaseDirectory,
        new File(mvnLocation), Collections.emptyList(), "mvn");

    //Process start = mavenExecutor.start(Arrays.asList("--no-transfer-progress", "-V", "clean", "verify"));
    //FIXME: Need to think about the default options given for a IT.

    List<String> executionArguments = new ArrayList<>();
    List<String> defaultArguments = Arrays.asList("-Dmaven.repo.local=" + cacheDirectory.toString(), "--batch-mode",
        "-V");
    executionArguments.addAll(defaultArguments);

    if (hasActiveProfiles(methodName)) {
      String collect = Arrays.asList(getActiveProfiles(methodName)).stream().collect(joining(",", "-P", ""));
      executionArguments.add(collect);
    }

    if (isDebug(methodName)) {
      executionArguments.add("-X");
    }

    String[] resultingGoals = GoalPriority.goals(mavenIT.goals(), getGoals(methodName));
    executionArguments.addAll(Stream.of(resultingGoals).collect(toList()));

    Process start = mavenExecutor.start(executionArguments);

    int processCompletableFuture = start.waitFor();

    ExecutionResult executionResult = ExecutionResult.Successful;
    if (processCompletableFuture != 0) {
      executionResult = ExecutionResult.Failure;
    }

    MavenLog log = new MavenLog(mavenExecutor.getStdout(), mavenExecutor.getStdErr());
    MavenCacheResult mavenCacheResult = new MavenCacheResult(cacheDirectory.toPath());

    Model model = ProjectHelper.readProject(projectDirectory);
    MavenProjectResult mavenProjectResult = new MavenProjectResult(projectDirectory, model);

    MavenExecutionResult result = new MavenExecutionResult(executionResult, processCompletableFuture, log,
        mavenProjectResult, mavenCacheResult);

    nameSpace.put(Result.ExecutionResult + context.getUniqueId(), result);
    nameSpace.put(Result.LogResult + context.getUniqueId(), log);
    nameSpace.put(Result.CacheResult + context.getUniqueId(), mavenCacheResult);
    nameSpace.put(Result.ProjectResult + context.getUniqueId(), mavenProjectResult);
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
  }

  @Override
  public void afterAll(ExtensionContext context) {
  }

  private enum Result {
    BaseDirectory(Void.class), //????
    ExecutionResult(MavenExecutionResult.class),
    LogResult(MavenLog.class),
    CacheResult(MavenCacheResult.class),
    ProjectResult(MavenProjectResult.class);

    private Class<?> klass;

    Result(Class<?> klass) {
      this.klass = klass;
    }

    Class<?> getKlass() {
      return klass;
    }
  }
}
