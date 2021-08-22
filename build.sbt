import Dependencies._
import BuildSettings._
import sbt.url

ThisBuild / organization := "org.beangle.data"
ThisBuild / version := "5.3.24"

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
ThisBuild / resolvers += Resolver.mavenLocal

lazy val root = (project in file("."))
  .settings()
  .aggregate(jdbc,model,orm,hibernate,transfer)

lazy val jdbc = (project in file("jdbc"))
  .settings(
    name := "beangle-data-jdbc",
    commonSettings,
    libraryDependencies ++= (commonDeps ++ Seq(scalaxml,HikariCP,h2))
  )

lazy val model = (project in file("model"))
  .settings(
    name := "beangle-data-model",
    commonSettings,
    libraryDependencies ++= (commonDeps ++ Seq(commonsText))
  )
lazy val orm = (project in file("orm"))
  .settings(
    name := "beangle-data-orm",
    commonSettings,
    libraryDependencies ++= (commonDeps ++ Seq(javassist,jpa))
  ).dependsOn(model,jdbc)

lazy val hibernate = (project in file("hibernate"))
  .settings(
    name := "beangle-data-hibernate",
    commonSettings,
    libraryDependencies ++=  commonDeps,
    libraryDependencies ++= Seq(hibernateCore,h2,HikariCP,postgresql,springTx,springAop,springJdbc,hibernateJCache,ehcache),
  ).dependsOn(orm)

lazy val transfer = (project in file("transfer"))
  .settings(
    name := "beangle-data-transfer",
    commonSettings,
    libraryDependencies ++= (commonDeps ++ Seq(commonsCsv,jxls,poi,jxlsPoi))
  ).dependsOn(orm)

publish / skip := true
hibernate / Test / parallelExecution := false
