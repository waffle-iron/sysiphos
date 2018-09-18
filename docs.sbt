enablePlugins(ParadoxSitePlugin, ParadoxMaterialThemePlugin, GhpagesPlugin)

ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox)

sourceDirectory in Paradox := baseDirectory.value / "docs"

paradoxMaterialTheme in Paradox := {
  ParadoxMaterialTheme()
    .withColor("white", "cyan")
    .withLogoIcon("timer")
    .withCopyright("© Sysiphos contributors")
    .withRepository(uri("https://github.com/flowtick/sysiphos"))
    .withFont("Source Sans Pro", "Source Sans Pro")
}

paradoxProperties in Paradox ++= Map(
  "version" -> version.value,
  "github.base_url" -> "https://github.com/flowtick/sysiphos"
)

git.remoteRepo := "git@github.com:flowtick/sysiphos.git"
git.useGitDescribe := true