import org.beangle.parent.Dependencies._
import org.beangle.parent.Settings._
import sbt.Keys.libraryDependencies

ThisBuild / organization := "org.beangle.data"
ThisBuild / version := "5.4.2-SNAPSHOT"
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

val beangle_common_ver = "5.2.14"

val beangle_commons_core = "org.beangle.commons" %% "beangle-commons-core" % beangle_common_ver
val beangle_commons_text = "org.beangle.commons" %% "beangle-commons-text" % beangle_common_ver
val apache_common_jexl_ver = "3.2.1"

val commonDeps = Seq(beangle_commons_core, logback_classic % "test", logback_core % "test", scalatest)

lazy val root = (project in file("."))
  .settings()
  .aggregate(jdbc, orm, excel, csv, dbf, transfer)

lazy val jdbc = (project in file("jdbc"))
  .settings(
    name := "beangle-data-jdbc",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(scalaxml, HikariCP % "optional", h2 % "test"))
  )

lazy val orm = (project in file("orm"))
  .settings(
    name := "beangle-data-orm",
    common,
    libraryDependencies ++= commonDeps,
    libraryDependencies ++= Seq(beangle_commons_text, javassist, jpa),
    libraryDependencies ++= Seq(hibernate_core, spring_tx, spring_aop, spring_jdbc),
    libraryDependencies ++= Seq(hibernate_jcache % "optional", ehcache % "optional"),
    libraryDependencies ++= Seq(h2 % "test", HikariCP % "test", postgresql % "test")
  ).dependsOn(jdbc)

lazy val csv = (project in file("csv"))
  .settings(
    name := "beangle-data-csv",
    common,
    libraryDependencies ++= commonDeps
  )

lazy val dbf = (project in file("dbf"))
  .settings(
    name := "beangle-data-dbf",
    common,
    libraryDependencies ++= commonDeps
  )

lazy val excel = (project in file("excel"))
  .settings(
    name := "beangle-data-excel",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(poi_ooxml)),
    libraryDependencies += "org.apache.commons" % "commons-jexl3" % apache_common_jexl_ver
  )

lazy val transfer = (project in file("transfer"))
  .settings(
    name := "beangle-data-transfer",
    common,
    libraryDependencies ++= commonDeps
  ).dependsOn(orm, excel, csv)

publish / skip := true
orm / Test / parallelExecution := false
