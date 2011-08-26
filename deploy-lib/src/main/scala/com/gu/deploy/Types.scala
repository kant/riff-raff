package com.gu.deploy

import tasks._

trait PackageType {
  def name: String
  def pkg: Package

  def mkAction(actionName: String): Action = {
    if (actions.isDefinedAt(actionName)) {
      new Action {
        def resolve(host: Host) = actions(actionName)(host)
        def roles = pkg.roles
        def description = pkg.name + "." + actionName
        override def toString = "action " + description
      }
    } else {
      sys.error("Action %s is not supported on package %s of type %s" format (actionName, pkg.name, name))
    }
  }

  type ActionDefinition = PartialFunction[String, Host => List[Task]]
  val actions: ActionDefinition

  val defaultData: Map[String, String]
}

case class JettyWebappPackageType(pkg: Package) extends PackageType {
  val name = "jetty-webapp"
  val defaultData = Map("port" -> "8080")

  val actions: ActionDefinition = {
    case "deploy" => { host => List(
        BlockFirewall(host),
        CopyFile(host,"packages/%s" format pkg.name, "/jetty-apps/"),
        RestartAndWait(host, pkg.name, pkg.data("port")),
        UnblockFirewall(host)
      )
    }
  }

}


case class FilePackageType(pkg: Package) extends PackageType {
  val name = "file"
  val defaultData = Map.empty[String,String]

  val actions: ActionDefinition = {
    case "deploy" => host => List(CopyFile(host, "packages/%s" format (pkg.name), "/"))
  }
}

case class DemoPackageType(pkg: Package) extends PackageType {
  val name = "demo"
  val defaultData = Map.empty[String,String]

  val actions: ActionDefinition = {
    case "hello" => host => List(
      SayHello(host)
    )

    case "echo-hello" => host => List(
      EchoHello(host)
    )
  }
}
