import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*

organization := "org.beangle.data"
version := "5.12.10-SNAPSHOT"

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

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "6.3.3"
val hibernate_core = "org.beangle.hibernate" % "beangle-hibernate-core" % "7.4.7.Final"
val beangle_jdbc = "org.beangle.jdbc" % "beangle-jdbc" % "1.1.15"

lazy val root = (project in file("."))
  .settings(
    name := "beangle-data",
    common,
    publish / skip := true
  )
  .aggregate(model, hibernate)

lazy val model = (project in file("model"))
  .enablePlugins(MetaPlugin)
  .settings(
    name := "beangle-data-model",
    common,
    // 库项目只在测试期生成 beanmeta.idx，主 scope 不外溢到发布 jar
    Compile / metaIndex := Def.uncached(Option.empty[File]),
    libraryDependencies ++= Seq(beangle_commons, beangle_jdbc, jpa, slf4j),
    libraryDependencies ++= Seq(logback_classic % "test", logback_core % "test", scalatest),
    Test / parallelExecution := false
  )

lazy val hibernate = (project in file("hibernate"))
  .enablePlugins(MetaPlugin, ProxyPlugin)
  .settings(
    name := "beangle-data-hibernate",
    common,
    // 库项目只在测试期生成 beanmeta.idx 与懒加载代理，主 scope 不外溢到发布 jar
    Compile / metaIndex := Def.uncached(Option.empty[File]),
    Compile / proxyClasses := Def.uncached(Seq.empty[File]),
    libraryDependencies ++= Seq(hibernate_core, hibernate_jcache, spring_tx, spring_aop),
    libraryDependencies ++= Seq(byte_buddy % "optional"),
    libraryDependencies ++= Seq(logback_classic % "test", logback_core % "test", scalatest, ehcache % "test"),
    libraryDependencies ++= Seq(h2 % "test", HikariCP % "test", postgresql % "test"),
    Test / parallelExecution := false
  )
  .dependsOn(model)
