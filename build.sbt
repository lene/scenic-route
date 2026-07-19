import wartremover.WartRemover.autoImport.*

val scala3Version = "3.6.4"
val tapirV        = "1.11.10"
val http4sV       = "0.23.28"
val circeV        = "0.14.10"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name         := "scenic-route",
    organization := "dev.scenicroute",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,

    // Server is the packaged/`docker` entry point; `runMain scenicroute.Main` still runs the CLI.
    Compile / mainClass := Some("scenicroute.Server"),

    // ── strict compiler flags (compensate for partial Scala 3 WartRemover) ──
    scalacOptions ++= Seq(
      "-Werror",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Yexplicit-nulls",
      "-language:strictEquality",
      "-Xmax-inlines:64",
      "-deprecation",
      "-feature"
    ),

    // ── dependencies ────────────────────────────────────────────────────────
    libraryDependencies ++= Seq(
      "com.graphhopper"              % "graphhopper-core"    % "11.0",
      "com.graphhopper"              % "graphhopper-web-api" % "11.0",
      "org.tomlj"                    % "tomlj"               % "1.1.1",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirV,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"    % tapirV,
      "org.http4s"                  %% "http4s-ember-server" % http4sV,
      "org.http4s"                  %% "http4s-ember-client" % http4sV,
      "io.circe"                    %% "circe-parser"        % circeV,
      "org.scalameta"               %% "munit"               % "1.0.4" % Test
    ),

    // ── WartRemover ─────────────────────────────────────────────────────────
    wartremoverErrors ++= Warts.allBut(
      Wart.ImplicitParameter, // needed for munit implicit suites
      Wart.PlatformDefault    // toString on some GH types
    ),

    // ── scalafix: needs semanticdb ───────────────────────────────────────────
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,

    // ── test framework ───────────────────────────────────────────────────────
    testFrameworks += new TestFramework("munit.Framework")
  )
