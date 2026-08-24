import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*
import sbt.internal.util.complete.DefaultParsers.*

organization := "org.beangle.data"
version := "5.12.8-SNAPSHOT"

scmInfo := Some(
  ScmInfo(
    uri("https://github.com/beangle/data"),
    "scm:git@github.com:beangle/data.git"
  )
)

developers := List(
  Developer(
    id = "chaostone",
    name = "Tihua Duan",
    email = "duantihua@gmail.com",
    url = uri("http://github.com/duantihua")
  )
)

description := "The Beangle Data Library"
homepage := Some(uri("https://beangle.github.io/data/index.html"))

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "6.2.3-SNAPSHOT"
val beangle_jdbc = "org.beangle.jdbc" % "beangle-jdbc" % "1.1.12"

// 构建期 native-image 辅助任务（见 docs/native-image.md）
lazy val nativeImageConfig = inputKey[Unit]("Generate GraalVM native-image configs (build-time)")
lazy val libraryNativeImageConfig = inputKey[Unit]("Regenerate embedded META-INF/native-image configs for beangle-data jars")

lazy val root = (project in file("."))
  .settings(
    name := "beangle-data",
    common,
    publish / skip := true,

    // ---- 构建期 native-image 辅助任务 ----
    // sbt "nativeImageConfig --output target/native-image --engine PostgreSQL"
    // sbt "libraryNativeImageConfig"
    libraryNativeImageConfig := (hibernate / Compile / runMain).toTask(" org.beangle.data.hibernate.nativeimage.LibraryNativeImageConfig").value,
    nativeImageConfig := Def.inputTaskDyn {
      val args = spaceDelimited("<arg>").parsed
      (hibernate / Test / runMain).toTask(" org.beangle.data.hibernate.nativeimage.NativeImageConfigGen " + args.mkString(" "))
    }.evaluated
  )
  .aggregate(model, hibernate)

lazy val model = (project in file("model"))
  .settings(
    name := "beangle-data-model",
    common,
    libraryDependencies ++= Seq(beangle_commons, beangle_jdbc, jpa, slf4j),
    libraryDependencies ++= Seq(logback_classic % "test", logback_core % "test", scalatest),
    Test / parallelExecution := false
  )

lazy val hibernate = (project in file("hibernate"))
  .settings(
    name := "beangle-data-hibernate",
    common,
    libraryDependencies ++= Seq(hibernate_core, hibernate_jcache, spring_tx, spring_aop),
    libraryDependencies ++= Seq(logback_classic % "test", logback_core % "test", scalatest, ehcache % "test"),
    libraryDependencies ++= Seq(h2 % "test", HikariCP % "test", postgresql % "test"),
    Test / parallelExecution := false
  )
  .dependsOn(model)
