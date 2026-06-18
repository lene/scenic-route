import wartremover.WartRemover.autoImport.*

val scala3Version = "3.6.4"

lazy val root = (project in file("."))
  .settings(
    name         := "scenic-route",
    organization := "dev.scenicroute",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,

    // ── strict compiler flags (compensate for partial Scala 3 WartRemover) ──
    scalacOptions ++= Seq(
      "-Werror",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Yexplicit-nulls",
      "-language:strictEquality",
      "-Xmax-inlines:64",
      "-deprecation",
      "-feature",
    ),

    // ── dependencies ────────────────────────────────────────────────────────
    libraryDependencies ++= Seq(
      "com.graphhopper" % "graphhopper-core" % "11.0",
      "org.tomlj"       % "tomlj"            % "1.1.1",
      "org.scalameta"  %% "munit"            % "1.0.4" % Test,
    ),

    // ── WartRemover ─────────────────────────────────────────────────────────
    wartremoverErrors ++= Warts.allBut(
      Wart.ImplicitParameter,   // needed for munit implicit suites
      Wart.PlatformDefault,     // toString on some GH types
    ),

    // ── scalafix: needs semanticdb ───────────────────────────────────────────
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,

    // ── test framework ───────────────────────────────────────────────────────
    testFrameworks += new TestFramework("munit.Framework"),
  )
