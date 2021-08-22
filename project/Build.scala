import sbt.Keys._
import sbt._


object BuildSettings {
  val buildScalaVersion = "3.0.1"

  val commonSettings = Seq(
    organizationName := "The Beangle Software",
    licenses += ("GNU Lesser General Public License version 3", new URL("http://www.gnu.org/licenses/lgpl-3.0.txt")),
    startYear := Some(2005),
    scalaVersion := buildScalaVersion,
    scalacOptions := Seq("-Xtarget:11","-deprecation","-feature"),
    crossPaths := true,

    publishMavenStyle := true,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishM2Configuration := publishM2Configuration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),

    versionScheme := Some("early-semver"),
    pomIncludeRepository := { _ => false }, // Remove all additional repository other than Maven Central from POM
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    })
}

object Dependencies {
  val logbackVer = "1.2.4"
  val scalatestVer = "3.2.9"
  val scalaxmlVer = "2.0.1"
  val commonsVer = "5.2.5"
  val springVer = "5.3.6"
  val poiVer = "4.1.2"
  val jxlsVer = "2.10.0"
  val hibernateVer = "5.5.6.Final"
  val javaassitVer = "3.27.0-GA"
  val jpaVer = "3.0.0"

  val scalatest = "org.scalatest" %% "scalatest" % scalatestVer % "test"
  val scalaxml = "org.scala-lang.modules" %% "scala-xml" % scalaxmlVer
  val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVer % "test"
  val logbackCore = "ch.qos.logback" % "logback-core" % logbackVer % "test"

  val commonsCore = "org.beangle.commons" %% "beangle-commons-core" % commonsVer
  val commonsText = "org.beangle.commons" %% "beangle-commons-text" % commonsVer
  val commonsCsv = "org.beangle.commons" %% "beangle-commons-csv" % commonsVer

  val springTx = "org.springframework" % "spring-tx" % springVer
  val springAop = "org.springframework" % "spring-aop" % springVer
  val springJdbc = "org.springframework" % "spring-jdbc" % springVer

  val hibernateCore = "org.beangle.hibernate" % "beangle-hibernate-core" % hibernateVer
  val hibernateJCache = "org.hibernate" % "hibernate-jcache" % hibernateVer  % "test"
  val hibernateEhcache = "org.hibernate" % "hibernate-ehcache" % hibernateVer % "test"

  val javassist = "org.javassist" % "javassist" % javaassitVer
  val jpa = "jakarta.persistence" % "jakarta.persistence-api" % jpaVer

  val poi = "org.apache.poi" % "poi" % poiVer
  val jxls = "org.jxls" % "jxls" % jxlsVer
  val jxlsPoi = "org.jxls" % "jxls-poi" % jxlsVer

  val h2 = "com.h2database" % "h2" % "1.4.200" % "test"
  val postgresql = "org.postgresql" % "postgresql" % "42.2.20" % "test"
  val HikariCP = "com.zaxxer" % "HikariCP" % "4.0.3" % "optional"

  var commonDeps = Seq(commonsCore, logbackClassic, logbackCore, scalatest)
}

