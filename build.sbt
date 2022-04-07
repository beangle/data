import org.beangle.parent.Dependencies._
import org.beangle.parent.Settings._

ThisBuild / organization := "org.beangle.data"
ThisBuild / version := "5.3.28"
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/beangle/data"),
    "scm:git@github.com:beangle/data.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "chaostone",
    name  = "Tihua Duan",
    email = "duantihua@gmail.com",
    url   = url("http://github.com/duantihua")
  )
)

ThisBuild / description := "The Beangle Data Library"
ThisBuild / homepage := Some(url("https://beangle.github.io/data/index.html"))

val beangle_common_ver="5.2.13"
val beangle_doc_ver="0.0.10"

val beangle_commons_core = "org.beangle.commons" %% "beangle-commons-core" % beangle_common_ver
val beangle_commons_text = "org.beangle.commons" %% "beangle-commons-text" % beangle_common_ver
val beangle_commons_csv = "org.beangle.commons" %% "beangle-commons-csv" % beangle_common_ver
val beangle_doc_excel = "org.beangle.doc" %% "beangle-doc-excel" % beangle_doc_ver

val commonDeps = Seq(beangle_commons_core, logback_classic, logback_core, scalatest)

lazy val root = (project in file("."))
  .settings()
  .aggregate(jdbc,model,orm,hibernate,transfer)

lazy val jdbc = (project in file("jdbc"))
  .settings(
    name := "beangle-data-jdbc",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(scalaxml,HikariCP,h2))
  )

lazy val model = (project in file("model"))
  .settings(
    name := "beangle-data-model",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(beangle_commons_text))
  )
lazy val orm = (project in file("orm"))
  .settings(
    name := "beangle-data-orm",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(javassist,jpa))
  ).dependsOn(model,jdbc)

lazy val hibernate = (project in file("hibernate"))
  .settings(
    name := "beangle-data-hibernate",
    common,
    libraryDependencies ++=  commonDeps,
    libraryDependencies ++= Seq(hibernate_core,h2,HikariCP,postgresql,spring_tx,spring_aop,spring_jdbc,hibernate_jcache,ehcache),
  ).dependsOn(orm)

lazy val transfer = (project in file("transfer"))
  .settings(
    name := "beangle-data-transfer",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(beangle_commons_csv,beangle_doc_excel,poi))
  ).dependsOn(orm)

publish / skip := true
hibernate / Test / parallelExecution := false
