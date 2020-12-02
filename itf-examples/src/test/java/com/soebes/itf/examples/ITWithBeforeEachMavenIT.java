package com.soebes.itf.examples;

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

import com.soebes.itf.extension.assertj.MavenITAssertions;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import com.soebes.itf.jupiter.maven.MavenProjectResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

@MavenJupiterExtension
class ITWithBeforeEachMavenIT {

  @BeforeEach
  //TODO: Inject also a directory which contains `project` ? (MavenProjectResult)
  //TODO: testMethodProjectFolder should be made part of MavenProjectResult as well! => simplifies the following code
  void beforeEach(MavenProjectResult project) {
    File testMethodProjectFolder = new File(this.getClass().getResource("/").getFile(), "com/soebes/itf/examples/ITWithBeforeEachMavenIT/the_first_test_case");
    List<String> expectedElements = createElements(testMethodProjectFolder);

    List<String> actualElements = createElements(new File(project.getBaseDir(), "project")); //HINT: "project" hard coded?

    MavenITAssertions.assertThat(actualElements).containsExactlyInAnyOrderElementsOf(expectedElements);
  }

  private List<String> createElements(File xdirectory) {
    Collection<File> expectedCollectedFiles = FileUtils.listFilesAndDirs(xdirectory, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    expectedCollectedFiles.forEach(System.out::println);

    List<String> expectedElements = expectedCollectedFiles.stream().map(p -> p.toString().replace(xdirectory.getAbsolutePath(), "")).collect(toList());
    return expectedElements;
  }

  @MavenTest
  void the_first_test_case(MavenExecutionResult result) {
    System.out.println("result = " + result);
  }
}
