// PARAMS
def java_cmd = "java -jar \"C:\\Program Files (x86)\\DBmaestro\\DOP Server\\Agent\\DBmaestroAgent.jar\""
def sep = "\\"
def server = "t21-jc" //hostname of dbmaestro server
def dbmNode = "master" //node name for Jenkins agent installed in DBmaestro server
def pipeline = "MSSQL_P2" //dbmaestro project name
def username = "poc@dbmaestro.com"
def password = "VmzU9NIDff1BALGXgsh58XXIg89FH7U5" //token taken from "My Profile", not the actual password

// START THE AUTOMATION
def credential = "-AuthType DBmaestroAccount -UserName \"_USER_\" -Password \"_PASS_\""
credential = credential.replaceFirst("_USER_", username)
credential = credential.replaceFirst("_PASS_", password)

environment = "PROD"
stage("PROD") {
    node (dbmNode) {
        dbmaestroRollback(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.RollbackTo
            , server: server
            , createScriptsOnly: false
            , credential: credential
        )
    }
}

environment = "UAT2"
stage("UAT2") {
    node (dbmNode) {
        dbmaestroRollback(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.RollbackTo
            , server: server
            , createScriptsOnly: false
            , credential: credential
        )
    }
}

environment = "UAT1"
stage("UAT1") {
    node (dbmNode) {
        dbmaestroRollback(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.RollbackTo
            , server: server
            , createScriptsOnly: false
            , credential: credential
        )
    }
}

environment = "QA"
stage("QA") {
    node (dbmNode) {
        dbmaestroRollback(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.RollbackTo
            , server: server
            , createScriptsOnly: false
            , credential: credential
        )
    }
}

environment = "RS"
stage("RS") {
    node (dbmNode) {
        dbmaestroRollback(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.RollbackTo
            , server: server
            , createScriptsOnly: false
            , credential: credential
        )
    }
}

def dbmaestroRollback(Map config=[:])
{
    def createScriptsOnlySwitch = ""
    if(config.createScriptsOnly){
        createScriptsOnlySwitch = "-CreateScriptsOnly True"
        msgbox "Creating Scripts to Rollback ${config.environment} to ${config.version}"
    }
    else{
        msgbox "Rolling Back ${config.environment} to ${config.version}"
    }
    bat "${config.java_cmd} -Rollback ${createScriptsOnlySwitch} -ProjectName ${config.pipeline} -EnvName \"${config.environment}\" -PackageName ${config.version} -Server ${config.server} ${config.credential}"
}

def msgbox(msg, def mtype = "nosep") {
  def tot = 80
  def start = ""
  def res = ""
  msg = (msg.size() > 65) ? msg[0..64] : msg
  def ilen = tot - msg.size()
  if (mtype == "sep"){
    start = "#${"-" * (ilen/2).toInteger()} ${msg} "
    res = "${start}${"-" * (tot - start.size() + 1)}#"
  }else{
    res = "#${"-" * tot}#\n"
    start = "#${" " * (ilen/2).toInteger()} ${msg} "
    res += "${start}${" " * (tot - start.size() + 1)}#\n"
    res += "#${"-" * tot}#\n"   
  }
  println res
}
