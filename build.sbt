/*
 * Copyright 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.typesafe.sbt.SbtGit.GitKeys.gitRemoteRepo
import sbt.Def

val algebirdVersion = "0.13.8"
val breezeVersion = "1.2"
val circeVersion = "0.14.1"
val commonsMathVersion = "3.6.1"
val flinkVersion = "1.13.1"
val hadoopVersion = "3.3.1"
val paradiseVersion = "2.1.1"
val scalacheckVersion = "1.15.3"
val scalatestVersion = "3.2.3"
val scaldingVersion = "0.17.4"
val scioVersion = "0.10.4"
val simulacrumVersion = "1.0.1"
val sparkVersion = "3.1.2"
val tensorflowVersion = "0.3.2"
val xgBoostVersion = "1.3.1"

val previousVersion = "0.8.0"

ThisBuild / scalafixDependencies += "org.typelevel" %% "simulacrum-scalafix" % "0.5.0"

val CompileTime = config("compile-time").hide

lazy val commonSettings = Seq(
  organization := "com.spotify",
  name := "featran",
  description := "Feature Transformers",
  scalaVersion := "2.12.14",
  scalacOptions ++= commonScalacOptions,
  scalacOptions ++= {
    if (scalaBinaryVersion.value == "2.13")
      Nil
    else
      Seq(
        "-Xfuture",
        "-Yno-adapted-args"
      )
  },
  scalacOptions ++= {
    if (isDotty.value) Seq("-source:3.0-migration", "-rewrite") else Nil
  },
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked"),
  Compile / doc / javacOptions := Seq("-source", "1.8"),
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "3"),
  libraryDependencies ++= Seq(
    ("org.typelevel" %% "simulacrum-scalafix-annotations" % "0.5.4" % CompileTime)
      .withDottyCompat(scalaVersion.value)
  ),
  ivyConfigurations += CompileTime,
  Compile / unmanagedClasspath ++= update.value.select(configurationFilter(CompileTime.name))
)

lazy val publishSettings = Seq(
  sonatypeProfileName := "com.spotify",
  licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/spotify/featran")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/spotify/featran.git"),
      "scm:git:git@github.com:spotify/featran.git"
    )
  ),
  developers := List(
    Developer(
      id = "sinisa_lyh",
      name = "Neville Li",
      email = "neville.lyh@gmail.com",
      url = url("https://twitter.com/sinisa_lyh")
    ),
    Developer(
      id = "rwhitcomb",
      name = "Richard Whitcomb",
      email = "richwhitjr@gmail.com",
      url = url("https://twitter.com/rwhitcomb")
    ),
    Developer(
      id = "ravwojdyla",
      name = "Rafal Wojdyla",
      email = "ravwojdyla@gmail.com",
      url = url("https://twitter.com/ravwojdyla")
    ),
    Developer(
      id = "fallonfofallon",
      name = "Fallon Chen",
      email = "fallon@spotify.com",
      url = url("https://twitter.com/fallonfofallon")
    ),
    Developer(
      id = "andrew_martin92",
      name = "Andrew Martin",
      email = "andrewsmartin.mg@gmail.com",
      url = url("https://twitter.com/andrew_martin92")
    ),
    Developer(
      id = "regadas",
      name = "Filipe Regadas",
      email = "filiperegadas@gmail.com",
      url = url("https://twitter.com/regadas")
    ),
    Developer(
      id = "slhansen",
      name = "Samantha Hansen",
      email = "slhansen@spotify.com",
      url = url("https://github.com/slhansen")
    )
  )
)

lazy val featranSettings = commonSettings ++ publishSettings

lazy val root: Project = project
  .in(file("."))
  .enablePlugins(GhpagesPlugin, ScalaUnidocPlugin)
  .settings(featranSettings)
  .settings(
    crossScalaVersions := Seq("2.12.14"),
    ScalaUnidoc / siteSubdirName := "api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    gitRemoteRepo := "git@github.com:spotify/featran.git",
    // com.spotify.featran.java pollutes namespaces and breaks unidoc class path
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(java) -- inProjects(
      examples
    ),
    makeSite / mappings ++= Seq(
      file("site/index.html") -> "index.html",
      file("examples/target/site/Examples.scala.html") -> "examples/Examples.scala.html"
    ),
    publish / skip := true,
    mimaFailOnNoPrevious := false
  )
  .aggregate(
    core,
    java,
    flink,
    scalding,
    scio,
    spark,
    numpy,
    tensorflow,
    xgboost
  )

lazy val core: Project = project
  .in(file("core"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-core"))
  .settings(
    name := "core",
    moduleName := "featran-core",
    description := "Feature Transformers",
    crossScalaVersions := Seq("3.0.0-M3", "2.12.14", "2.13.6"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % "test",
      "org.apache.commons" % "commons-math3" % commonsMathVersion % "test",
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % "test"
    ),
    libraryDependencies ++= Seq(
      "com.twitter" %% "algebird-core" % algebirdVersion,
      "org.scalanlp" %% "breeze" % breezeVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    ).map(_.withDottyCompat(scalaVersion.value))
  )

lazy val java: Project = project
  .in(file("java"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-java"))
  .settings(
    name := "java",
    moduleName := "featran-java",
    description := "Feature Transformers - java",
    crossScalaVersions := Seq("2.12.14", "2.13.6"),
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % "test",
      "org.scalatest" %% "scalatest" % scalatestVersion % "test"
    )
  )
  .dependsOn(
    core,
    core % "test->test",
    tensorflow,
    xgboost
  )

lazy val flink: Project = project
  .in(file("flink"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-flink"))
  .settings(
    name := "flink",
    moduleName := "featran-flink",
    description := "Feature Transformers - Flink",
    crossScalaVersions := Seq("2.12.14"),
    libraryDependencies ++= Seq(
      "org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
      "org.apache.flink" %% "flink-clients" % flinkVersion % "provided",
      "org.scalatest" %% "scalatest" % scalatestVersion % "test"
    )
  )
  .dependsOn(
    core,
    core % "test->test"
  )

lazy val scalding: Project = project
  .in(file("scalding"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-scalding"))
  .settings(
    name := "scalding",
    moduleName := "featran-scalding",
    description := "Feature Transformers - Scalding",
    resolvers += "Concurrent Maven Repo" at "https://conjars.org/repo",
    crossScalaVersions := Seq("2.12.14"),
    libraryDependencies ++= Seq(
      "com.twitter" %% "scalding-core" % scaldingVersion % "provided",
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion % "provided",
      "org.scalatest" %% "scalatest" % scalatestVersion % "test"
    )
  )
  .dependsOn(
    core,
    core % "test->test"
  )

lazy val scio: Project = project
  .in(file("scio"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-scio"))
  .settings(
    name := "scio",
    moduleName := "featran-scio",
    description := "Feature Transformers - Scio",
    crossScalaVersions := Seq("2.12.14", "2.13.6"),
    libraryDependencies ++= Seq(
      "com.spotify" %% "scio-core" % scioVersion % "provided",
      "com.spotify" %% "scio-test" % scioVersion % "test"
    )
  )
  .dependsOn(
    core,
    core % "test->test"
  )

lazy val spark: Project = project
  .in(file("spark"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-spark"))
  .settings(
    name := "spark",
    moduleName := "featran-spark",
    description := "Feature Transformers - Spark",
    crossScalaVersions := Seq("2.12.14"),
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
      "org.scalatest" %% "scalatest" % scalatestVersion % "test"
    )
  )
  .dependsOn(
    core,
    core % "test->test"
  )

lazy val numpy: Project = project
  .in(file("numpy"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-numpy"))
  .settings(
    name := "numpy",
    moduleName := "featran-numpy",
    description := "Feature Transformers - NumPy",
    crossScalaVersions := Seq("3.0.0-M3", "2.12.14", "2.13.6"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % "test"
    )
  )
  .dependsOn(core)

lazy val tensorflow: Project = project
  .in(file("tensorflow"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-tensorflow"))
  .settings(
    name := "tensorflow",
    moduleName := "featran-tensorflow",
    description := "Feature Transformers - TensorFlow",
    crossScalaVersions := Seq("3.0.0-M3", "2.12.14", "2.13.6"),
    libraryDependencies ++= Seq(
      "org.tensorflow" % "tensorflow-core-api" % tensorflowVersion
    ),
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % "test"
    )
  )
  .dependsOn(
    core,
    core % "test->test"
  )

lazy val xgboost: Project = project
  .in(file("xgboost"))
  .settings(featranSettings)
  .settings(mimaSettings("featran-xgboost"))
  .settings(
    name := "xgboost",
    moduleName := "featran-xgboost",
    description := "Feature Transformers - XGBoost",
    crossScalaVersions := Seq("3.0.0-M3", "2.12.14", "2.13.6"),
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % "test"
    )
  )
  .dependsOn(
    core,
    core % "test->test"
  )

lazy val examples: Project = project
  .in(file("examples"))
  .settings(featranSettings)
  .settings(soccoSettings)
  .settings(
    crossScalaVersions := Seq("2.12.14", "2.13.6"),
    name := "examples",
    moduleName := "featran-examples",
    description := "Feature Transformers - examples",
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % scalacheckVersion
    ),
    publish / skip := true
  )
  .dependsOn(core, scio, tensorflow)

lazy val jmh: Project = project
  .in(file("jmh"))
  .settings(featranSettings)
  .settings(
    crossScalaVersions := Seq("2.12.14"),
    name := "jmh",
    description := "Featran JMH Microbenchmarks",
    Jmh / sourceDirectory := (Test / sourceDirectory).value,
    Jmh / classDirectory := (Test / classDirectory).value,
    Jmh / dependencyClasspath := (Test / dependencyClasspath).value,
    publish / skip := true
  )
  .dependsOn(
    core,
    tensorflow
  )
  .enablePlugins(JmhPlugin)

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Ywarn-unused",
  "-Ywarn-dead-code",
  "-Xcheckinit",
  "-Xlint:adapted-args",
  "-Xlint:delayedinit-select",
  "-Xlint:doc-detached",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:nullary-unit",
  "-Xlint:option-implicit",
  "-Xlint:poly-implicit-overload",
  "-Xlint:private-shadow",
  "-Xlint:stars-align",
  "-Xlint:type-parameter-shadow"
)

lazy val soccoSettings = if (sys.env.contains("SOCCO")) {
  Seq(
    scalacOptions ++= Seq(
      "-P:socco:out:examples/target/site",
      "-P:socco:package_com.spotify.featran:http://spotify.github.io/featran/api",
      "-P:socco:package_com.spotify.scio:http://spotify.github.io/scio/api"
    ),
    autoCompilerPlugins := true,
    addCompilerPlugin("com.criteo.socco" %% "socco-plugin" % "0.1.9")
  )
} else {
  Nil
}

def mimaSettings(moduleName: String): Seq[Def.Setting[_]] =
  Def.settings(
    mimaPreviousArtifacts := {
      dynverGitDescribeOutput.value
        .map(_.ref.value.tail)
        .filter(VersionNumber(_).matchesSemVer(SemanticSelector(s">=${previousVersion}")))
        .map("com.spotify" %% moduleName % _)
        .toSet
    }
  )
