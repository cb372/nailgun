package bloop.sbt.integrations

import ch.epfl.scala.sbt.release.{ReleaseEarlyPlugin, AutoImported => ReleaseEarlyNamespace}
import com.typesafe.sbt.SbtPgp
import com.typesafe.sbt.SbtPgp.autoImport.PgpKeys
import sbt.{AutoPlugin, Compile, Def, Keys, file, url, Developer}
import sbtdynver.{DynVerPlugin, GitDescribeOutput}

object BloopPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = ReleaseEarlyPlugin && SbtPgp && DynVerPlugin
  val autoImport = BloopKeys

  override def globalSettings: Seq[Def.Setting[_]] = BloopPluginImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] = BloopPluginImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] = BloopPluginImplementation.projectSettings
}

object BloopKeys {}

object BloopPluginImplementation {
  private def GitHub(org: String, project: String): java.net.URL =
    url(s"https://github.com/$org/$project")
  private def GitHubDev(handle: String, fullName: String, email: String) =
    Developer(handle, fullName, email, url(s"https://github.com/$handle"))

  private final val ThisRepo = GitHub("scalacenter", "bloop")
  val globalSettings: Seq[Def.Setting[_]] = List(
    Keys.startYear := Some(2017),
    Keys.autoAPIMappings := true,
    Keys.publishMavenStyle := true,
    Keys.homepage := Some(ThisRepo),
    Keys.licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    Keys.developers := List(
      GitHubDev("mlamb", "Marty Lamb", "mlabm@martiansoftware.com"),
      GitHubDev("jvican", "Jorge Vicente Cantero", "jorge@vican.me"),
      GitHubDev("olafurpg", "Ólafur Páll Geirsson", "olafurpg@gmail.com"),
    ),
  )

  val buildSettings: Seq[Def.Setting[_]] = List(
    Keys.organization := "ch.epfl.scala",
    ReleaseEarlyNamespace.releaseEarlyWith := ReleaseEarlyNamespace.SonatypePublisher,
    PgpKeys.pgpPublicRing := {
      if (Keys.insideCI.value) file("/drone/.gnupg/pubring.asc")
      else PgpKeys.pgpPublicRing.value
    },
    PgpKeys.pgpSecretRing := {
      if (Keys.insideCI.value) file("/drone/.gnupg/secring.asc")
      else PgpKeys.pgpPublicRing.value
    }
  )

  import DynVerPlugin.{autoImport => DynVerKeys}
  val projectSettings: Seq[Def.Setting[_]] = List(
    ReleaseEarlyNamespace.releaseEarlyPublish := PgpKeys.publishSigned.value,
    Keys.publishArtifact in (Compile, Keys.packageDoc) := {
      val output = DynVerKeys.dynverGitDescribeOutput.value
      val version = Keys.version.value
      BloopDefaults.publishDocAndSourceArtifact(output, version)
    },
    Keys.publishArtifact in (Compile, Keys.packageSrc) := {
      val output = DynVerKeys.dynverGitDescribeOutput.value
      val version = Keys.version.value
      BloopDefaults.publishDocAndSourceArtifact(output, version)
    },
  )

  object BloopDefaults {

    /**
     * This setting figures out whether the version is a snapshot or not and configures
     * the source and doc artifacts that are published by the build.
     *
     * Snapshot is a term with no clear definition. In this code, a snapshot is a revision
     * that is dirty, e.g. has time metadata in its representation. In those cases, the
     * build will not publish doc and source artifacts by any of the publishing actions.
     */
    def publishDocAndSourceArtifact(info: Option[GitDescribeOutput], version: String): Boolean = {
      val isStable = info.map(_.dirtySuffix.value.isEmpty)
      !isStable.map(stable => !stable || version.endsWith("-SNAPSHOT")).getOrElse(false)
    }
  }
}