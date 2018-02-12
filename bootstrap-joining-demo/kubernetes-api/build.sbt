import com.typesafe.sbt.packager.docker._

enablePlugins(JavaServerAppPackaging)

/*
libraryDependencies += Seq(
  // 1) we want to use akka-management
  "akka-management",

  // 2) we want to use the bootstrap
  "akka-management-cluster-bootstrap",

  // 3) the initial contact points should be located via DNS:
  "akka-discovery-dns",

  // extra)
  // ""akka-management-cluster-http" // optional, if you want to inspect the cluster as well
*/

version := "1.3.3.7" // we hard-code the version here, it could be anything really

// use += to add an item to a Sequence
dockerCommands += Cmd("USER", "root")

// use ++= to merge a sequence with an existing sequence

/* ENABLE THESE IF YOU WANT TO MANUALLY DO DNSLOOKUPS IN THE CONTAINER (FOR DEBUGGING)

dockerCommands ++= Seq(
  ExecCmd("RUN", "apt-get", "update"),
  ExecCmd("RUN", "apt-get", "install", "-y", "dnsutils")
)

*/
