name := "unicorn-shell"

mainClass in Compile := Some("unicorn.shell.Main")

// SBT native packager
enablePlugins(JavaAppPackaging)

maintainer := "Haifeng Li <Haifeng.Li@ADP.COM>"

packageName := "unicorn"

packageSummary := "ADP Unicorn"

packageDescription := "ADP Unicorn"

executableScriptName := "unicorn"

bashScriptExtraDefines += """addJava "-Dsmile.home=${app_home}""""

bashScriptExtraDefines += """addJava "-Dscala.repl.autoruncode=${app_home}/init.scala""""

bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf""""

bashScriptExtraDefines += """addJava "-Dlog4j.configuration=${app_home}/../conf/log4j.properties""""

// G1 garbage collector
bashScriptExtraDefines += """addJava "-XX:+UseG1GC""""

// Optimize string duplication, which happens a lot when parsing a data file
bashScriptExtraDefines += """addJava "-XX:+UseStringDeduplication""""

// SBT BuildInfo
enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "unicorn.shell"

buildInfoOptions += BuildInfoOption.BuildTime

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.11.7"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.2"

libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.2"
