import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*

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

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "6.3.0-SNAPSHOT"
val beangle_jdbc = "org.beangle.jdbc" % "beangle-jdbc" % "1.1.13-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "beangle-data",
    common,
    publish / skip := true
  )
  .aggregate(model, hibernate, sampleNative)

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

// ---- GraalVM native-image sample ----
lazy val patchHibernateJar = taskKey[File]("Patch beangle-hibernate-core JAR to replace BytecodeProvider SPI")

lazy val sampleNative = (project in file("samples/native"))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "beangle-data-sample-native",
    common,
    publish / skip := true,
    Compile / mainClass := Some("org.beangle.data.samples.nativeapp.NativeApp"),
    nativeImageGraalHome := Def.uncached {
      file(sys.env.getOrElse("GRAALVM_HOME",
        sys.env.getOrElse("JAVA_HOME", "/home/chaostone/local/graalvm-jdk-21"))).toPath
    },
    nativeImageInstalled := true,
    nativeImageOptions ++= Seq(
      "--no-fallback",
      "--enable-url-protocols=jar,resource",
      "-H:+AddAllCharsets",
      "-H:ReflectionConfigurationFiles=" + baseDirectory.value + "/src/main/resources/native-image/reflect-config.json",
      "-H:ResourceConfigurationFiles=" + baseDirectory.value + "/src/main/resources/native-image/resource-config.json",
      "--initialize-at-run-time=org.h2.Driver,org.ehcache,com.github.benmanes.caffeine,java.awt,sun.awt,com.sun.jmx,org.beangle,org.beangle.data.samples.nativeapp",
      "-Dhibernate.bytecode.use_reflection_optimizer=true",
      "-Dnet.bytebuddy.reproducible=true",
      "-H:+ReportExceptionStackTraces",
      "--report-unsupported-elements-at-runtime"
    ),
    libraryDependencies ++= Seq(h2, HikariCP, logback_classic, logback_core),
    libraryDependencies ++= Seq(
      "com.github.ben-manes.caffeine" % "caffeine" % "3.2.0",
      "com.github.ben-manes.caffeine" % "jcache" % "3.2.0",
      "javax.cache" % "cache-api" % "1.1.1",
      "org.hibernate.orm" % "hibernate-graalvm" % "7.4.5.Final"
    ),
    // Exclude byte-buddy: use none.BytecodeProviderImpl instead
    excludeDependencies += ExclusionRule(organization = "net.bytebuddy", name = "byte-buddy"),
    // Patch JAR before nativeImage runs
    patchHibernateJar := Def.uncached {
      val log = streams.value.log
      val allJars = (Compile / fullClasspath).value
      val hibernateJarPath = allJars.find(_.data.toString.contains("beangle-hibernate-core")).map(_.data.toString).getOrElse(
        sys.error("beangle-hibernate-core JAR not found in classpath"))
      val hibernateJar = new File(hibernateJarPath)
      val patchedDir = target.value / "patched-jar"
      val patchedJar = new File(patchedDir, hibernateJar.getName)
      if (!patchedJar.exists()) {
        log.info(s"Patching ${hibernateJar.getName} to replace BytecodeProvider SPI...")
        patchedDir.mkdirs()
        scala.sys.process.Process(Seq("jar", "xf", hibernateJar.getAbsolutePath), patchedDir).!!
        val oldSpi = new File(patchedDir, "META-INF/services/org.hibernate.bytecode.spi.BytecodeProvider")
        if (oldSpi.exists()) oldSpi.delete()
        val spiDir = new File(patchedDir, "META-INF/services")
        spiDir.mkdirs()
        val pw = new java.io.PrintWriter(new File(spiDir, "org.hibernate.bytecode.spi.BytecodeProvider"))
        pw.println("org.hibernate.bytecode.internal.none.BytecodeProviderImpl")
        pw.close()
        val tmpJar = new File(patchedDir, hibernateJar.getName + ".tmp")
        scala.sys.process.Process(Seq("jar", "cf", tmpJar.getAbsolutePath, "-C", patchedDir.getAbsolutePath, "."), patchedDir).!!
        tmpJar.renameTo(patchedJar)
        tmpJar.delete()
        log.info(s"Patched JAR: ${patchedJar.getAbsolutePath}")
      }
      patchedJar
    },
    nativeImage := (nativeImage.dependsOn(patchHibernateJar)).value
  )
  .dependsOn(hibernate)
