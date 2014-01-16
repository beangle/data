import sbt._
import Keys._

object BuildSettings {
  val buildOrganization = "org.beangle.data"
  val buildVersion = "4.0.2-SNAPSHOT"
  val buildScalaVersion = "2.10.3"

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := buildOrganization,
    version := buildVersion,
    scalaVersion := buildScalaVersion,
    shellPrompt := ShellPrompt.buildShellPrompt,
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-target:jvm-1.6"),
    unmanagedJars in Compile += file(sys.props("java.home") + "/../lib/tools.jar"),
    crossPaths := false)
}

object ShellPrompt {

  object devnull extends ProcessLogger {
    def info(s: ⇒ String) {}

    def error(s: ⇒ String) {}

    def buffer[T](f: ⇒ T): T = f
  }

  def currBranch = (
    ("git status -sb" lines_! devnull headOption)
    getOrElse "-" stripPrefix "## ")

  val buildShellPrompt = {
    (state: State) ⇒
      {
        val currProject = Project.extract(state).currentProject.id
        "%s:%s:%s> ".format(
          currProject, currBranch, BuildSettings.buildVersion)
      }
  }
}

object Dependencies {
  val h2Ver = "1.3.172"
  val slf4jVer = "1.6.6"
  val mockitoVer = "1.9.5"
  val logbackVer = "1.0.7"
  val scalatestVer = "2.0.M5b"

  val slf4j = "org.slf4j" % "slf4j-api" % slf4jVer
  val scalatest = "org.scalatest" % "scalatest_2.10" % scalatestVer % "test"
  val mockito = "org.mockito" % "mockito-core" % mockitoVer % "test"

  val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVer % "test"
  val logbackCore = "ch.qos.logback" % "logback-core" % logbackVer % "test"

  val h2 = "com.h2database" % "h2" % h2Ver
  val sqlserver = "net.sourceforge.jtds" % "jtds" % "1.3.1"
  val dbcp = "commons-dbcp" % "commons-dbcp" % "1.4"
  val jpa = "org.hibernate.javax.persistence" % "hibernate-jpa-2.0-api" % "1.0.1.Final"
  val validation = "javax.validation" % "validation-api" % "1.0.0.GA"

  val commons_jdbc = "org.beangle.commons" % "beangle-commons-jdbc" % "4.0.1"
  val freemarker = "org.freemarker" % "freemarker" % "2.3.20"
  val umlgraph = "org.umlgraph" % "umlgraph" % "5.6.6"
}

object Resolvers {
  val m2repo = "Local Maven2 Repo" at "file://" + Path.userHome + "/.m2/repository"
}

object BeangleBuild extends Build {

  import Dependencies._
  import BuildSettings._
  import Resolvers._

  val commonDeps = Seq(slf4j, logbackClassic, logbackCore, scalatest, commons_jdbc, h2, sqlserver, dbcp)

  lazy val data = Project("beangle-data", file("."), settings = buildSettings) aggregate (data_model,data_jpa,data_jdbc,data_report, data_conversion, data_lint)

  lazy val data_model = Project("beangle-data-model",file("model"),
    settings = buildSettings ++ Seq(libraryDependencies ++= commonDeps ++ Seq(commons_core))
      ++ Seq(resolvers += m2repo))

  lazy val data_jpa = Project("beangle-data-jpa",file("jpa"),
    settings = buildSettings ++ Seq(libraryDependencies ++= commonDeps ++ Seq(validation, jpa))
      ++ Seq(resolvers += m2repo)) dependsOn (data_model)

  lazy val data_jdbc = Project("beangle-data-jdbc",file("jdbc"),
    settings = buildSettings ++ Seq(libraryDependencies ++= commonDeps ++ Seq(h2, dbcp,commons_core))
      ++ Seq(resolvers += m2repo)) 

  lazy val data_conversion = Project("beangle-data-conversion",file("conversion"),
    settings = buildSettings ++ Seq(libraryDependencies ++= commonDeps) ++ Seq(resolvers += m2repo))

  lazy val data_report = Project("beangle-data-report",file("report"),
    settings = buildSettings ++ Seq(libraryDependencies ++= commonDeps ++ Seq(freemarker, umlgraph)) ++ Seq(resolvers += m2repo))

  lazy val data_lint = Project("beangle-data-lint",file("lint"),
    settings = buildSettings ++ Seq(libraryDependencies ++= commonDeps) ++ Seq(resolvers += m2repo))
}
