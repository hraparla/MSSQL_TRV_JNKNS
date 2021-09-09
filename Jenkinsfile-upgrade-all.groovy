// PARAMS
def java_cmd = "java -jar \"C:\\Program Files (x86)\\DBmaestro\\DOP Server\\Agent\\DBmaestroAgent.jar\""
def sep = "\\"
def server = "t21-travelers" //hostname of dbmaestro server
def dbmNode = "master" //node name for Jenkins agent installed in DBmaestro server
def pipeline = "MSSQL_P2" //dbmaestro project name
def username = "poc@dbmaestro.com"
def password = "VmzU9NIDff1BALGXgsh58XXIg89FH7U5" //token taken from "My Profile", not the actual password

// START THE AUTOMATION
def credential = "-AuthType DBmaestroAccount -UserName \"_USER_\" -Password \"_PASS_\""
credential = credential.replaceFirst("_USER_", username)
credential = credential.replaceFirst("_PASS_", password)

//DRY RUN ENV (PRECHECK)
stage("PreCheck") {
    node (dbmNode) {
        //PreRun in DryRun environment first
        if(env.PreCheck == 'Yes'){
            dbmaestroPreCheck(
                java_cmd: java_cmd
                , pipeline: pipeline
                , version: env.Version
                , server: server
                , credential: credential
            )
        }
    }
}

// DEPLOY TO RELEASE SOURCE
environment = "RS"
stage(environment){
    node (dbmNode) {
        dbmaestroUpgrade(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.Version
            , server: server
            , credential: credential
        )
    }
}

//  DEPLOY TO QA
environment = "QA"
stage(environment) {
    node (dbmNode) {
        dbmaestroUpgrade(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.Version
            , server: server
            , credential: credential
        )
    }   
}

//  DEPLOY TO QA2
environment = "QA2"
stage(environment) {
    node (dbmNode) {
        dbmaestroUpgrade(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.Version
            , server: server
            , credential: credential
        )
   }
}

//  DEPLOY TO UAT
environment = "UAT"
stage(environment) {
    node (dbmNode) {
        dbmaestroUpgrade(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.Version
            , server: server
            , credential: credential
        )
   }
}

    stage ('Approval gate') {
        def message = 'APPROVE RELEASE TO PROD?'
        echo """=======================================================================================================================  
${message}
======================================================================================================================="""
        timeout(time: 30, unit: 'MINUTES') {
            def userInput = input(
                id: 'userInput', message: "$message", parameters: [
                    [$class: 'TextParameterDefinition', defaultValue: 'I approve the deployment', description: 'To proceed, type I approve the deployment', name: 'Review deployment artifacts before proceeding']
                ]
            )
            if (userInput.indexOf('I approve the deployment') == -1) {
                currentBuild.result = 'ABORTED'
                error('Deployment aborted')
            }
        }
    } 

//  DEPLOY TO PROD
environment = "PROD"
stage(environment) {
    node (dbmNode) {
        dbmaestroUpgrade(
            java_cmd: java_cmd
            , pipeline: pipeline
            , environment: environment
            , version: env.Version
            , server: server
            , credential: credential
        )
    }
}


def dbmaestroPreCheck(Map config=[:])
{
    msgbox "Performing PreCheck on Package ${config.version}"
    echo "Perform a policy enforcement and pre-validation on a Package. If a PreRun Environment has been created during Project creation, then a simulation is run in the PreRun Environment as well."
    bat "${config.java_cmd} -PreCheck -ProjectName ${config.pipeline} -PackageName ${config.version} -Server ${config.server} ${config.credential}"
}

def dbmaestroUpgrade(Map config=[:])
{
    msgbox "Upgrading ${config.environment} to ${config.version}"
    bat "${config.java_cmd} -Upgrade -ProjectName ${config.pipeline} -EnvName \"${config.environment}\" -PackageName ${config.version} -Server ${config.server} ${config.credential}"
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

