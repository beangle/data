import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*
import sbt.Keys.*

ThisBuild / organization := "org.beangle.data"
ThisBuild / version := "5.9.1-SNAPSHOT"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/beangle/data"),
    "scm:git@github.com:beangle/data.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "chaostone",
    name = "Tihua Duan",
    email = "duantihua@gmail.com",
    url = url("http://github.com/duantihua")
  )
)

ThisBuild / description := "The Beangle Data Library"
ThisBuild / homepage := Some(url("https://beangle.github.io/data/index.html"))

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "5.6.30"
val beangle_jdbc = "org.beangle.jdbc" % "beangle-jdbc" % "1.0.12"

lazy val root = (project in file("."))
  .settings(
    name := "beangle-model",
    common,
    libraryDependencies ++= Seq(beangle_commons, logback_classic % "test", logback_core % "test", scalatest),
    libraryDependencies ++= Seq(beangle_jdbc, javassist, jpa),
    libraryDependencies ++= Seq(spring_tx % "optional", spring_aop % "optional", spring_jdbc % "optional"),
    libraryDependencies ++= Seq(hibernate_jcache % "optional", hibernate_core % "optional", ehcache % "test"),
    libraryDependencies ++= Seq(h2 % "test", HikariCP % "test", postgresql % "test")
  )

Test / parallelExecution := false
